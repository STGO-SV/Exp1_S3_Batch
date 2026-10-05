"""Escala EFT real: discovery, TLS verificado, routing, finanzas, Kafka y failover individual.
python -B scripts/validate-eft-scale.py config|ready|routing|flow|outbox|failover|final
No escala ni elimina datos. Requiere stack preparado; failover restaura cada contenedor en finally.
"""
from pathlib import Path
from collections import Counter
import argparse, concurrent.futures, importlib.util, json, threading, time, urllib.request, uuid
spec=importlib.util.spec_from_file_location("eft",Path(__file__).with_name("validate-eft-compose.py"))
e=importlib.util.module_from_spec(spec);spec.loader.exec_module(e)
ROOT=e.ROOT;STATE=ROOT/".local/etapa6-state.json"
COMPOSE=["docker","compose","-f","docker-compose.yaml","-f","docker/compose.scale.yaml"]
SERVICES={"customer-service":8087,"account-service":8085,"payment-service":8086}
lock=threading.Lock();positions={}
def save(s): STATE.write_text(json.dumps(s,indent=2),encoding="utf-8")
def state(): return json.loads(STATE.read_text(encoding="utf-8"))
def instances():
    req=urllib.request.Request("http://localhost:8761/eureka/apps",headers={"Accept":"application/json"})
    with urllib.request.urlopen(req,timeout=10) as response:body=json.load(response)
    apps=body["applications"].get("application",[])
    if isinstance(apps,dict):apps=[apps]
    result={s:[] for s in SERVICES}
    for app in apps:
        name=app["name"].lower().removeprefix("banco-legacy-")
        if name not in result:continue
        entries=app.get("instance",[])
        if isinstance(entries,dict):entries=[entries]
        for v in entries:
            if v["status"]=="UP":
                result[name].append({"instanceId":v["instanceId"],"hostname":v["hostName"],
                    "ip":v["ipAddr"],"status":v["status"],"port":int(v["securePort"]["$"]),
                    "secure":v["securePort"]["@enabled"]=="true"})
    return result
def docker():
    result=[]
    for line in e.run(COMPOSE+["ps","--format","json"]).splitlines():
        v=json.loads(line)
        result.append({k:v[k] for k in ["ID","Name","Service","Image","State","Health","Ports"]}|
            {"restartCount":int(e.run(["docker","inspect","--format","{{.RestartCount}}",v["ID"]]).strip())})
    return result
def wait(predicate,timeout=180):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        if predicate():return
        time.sleep(3)
    raise AssertionError("Timeout esperando condición de escala")
def tokens():
    return (e.token("banco-domain-operator","OAUTH_DOMAIN_CLIENT_SECRET",
                  "accounts.read accounts.write customers.read customers.write","etapa6-domain"),
            e.token("banco-payment-operator","OAUTH_PAYMENT_CLIENT_SECRET",
                  "payments.read payments.write accounts.post accounts.post.read","etapa6-payment"))
def request(service,method,path,token=None,body=None,key=None,expected=None,replicas=None):
    choices=(replicas or instances())[service]
    assert choices,"Sin instancias UP: "+service
    with lock:
        position=positions.get(service,0);positions[service]=position+1
    selected=choices[position%len(choices)]
    port=SERVICES[service]
    options={"url":f"https://{service}:{port}{path}","request":method,
        "resolve":f"{service}:{port}:{selected['ip']}",
        "cacert":"/run/banco-legacy/banco-legacy-docker.crt",
        "connect-timeout":"5","max-time":"15","write-out":"\\n%{http_code} %{remote_ip}"}
    lines=["silent","show-error"]+[k+" = "+json.dumps(v) for k,v in options.items()]
    if token:lines.append("header = "+json.dumps("Authorization: Bearer "+token))
    if key:lines.append("header = "+json.dumps("Idempotency-Key: "+key))
    if body is not None:
        lines.extend(["header = "+json.dumps("Content-Type: application/json"),
                      "data = "+json.dumps(json.dumps(body))])
    output=e.run(["docker","exec","-i","banco-legacy-web-bff-1","curl","--config","-"],
                 input="\n".join(lines)+"\n",timeout=25)
    payload,last=output.rsplit("\n",1);code,ip=last.split()
    assert ip==selected["ip"],"Routing no coincide con Eureka"
    parsed=json.loads(payload) if payload.strip() else None
    record={"method":method,"url":options["url"],"instance":selected,"connectedIp":ip,
        "status":int(code),"request":body,"response":parsed,"idempotencyKey":key,
        "certificateAndServiceHostnameVerified":True}
    if expected is not None and int(code)!=expected:
        e.evidence("etapa6-error-http.json",record)
        raise AssertionError(f"{method} {service}{path}: {code} esperado {expected}: {e.safe(parsed)}")
    return record
def config():
    settings=json.loads(e.run(COMPOSE+["config","--format","json"]))
    summary={}
    for s in SERVICES:
        v=settings["services"][s]
        env=v["environment"]
        summary[s]={"ports":v.get("ports",[]),"container_name":v.get("container_name"),
            "expose":v.get("expose",[]),"volumes":v["volumes"],"healthcheck":v["healthcheck"],
            "environment":{k:env[k] for k in env if k.startswith(("EUREKA_","BANKING_HTTP_","BANKING_KAFKA_OUTBOX_","SPRING_KAFKA_CONSUMER_CLIENT","SPRING_CLOUD_LOADBALANCER_"))}}
        assert not v.get("ports") and not v.get("container_name")
    e.evidence("etapa6-02-compose-scale-config.json",summary)
def ready():
    wait(lambda:all(len(v)==2 for v in instances().values()))
    wait(lambda:all(sum(c["Service"]==s and c["Health"]=="healthy" for c in docker())==2 for s in SERVICES))
    containers=docker()
    assert all(sum(c["Service"]==s and c["Health"]=="healthy" for c in containers)==2 for s in SERVICES)
    assert all(c["restartCount"]==0 for c in containers if c["Service"] in SERVICES)
    registry=instances()
    assert all(len({v["instanceId"] for v in entries})==2 and len({v["ip"] for v in entries})==2 for entries in registry.values())
    assert all(v["secure"] and v["port"]==SERVICES[s] for s,entries in registry.items() for v in entries)
    assert all({v["instanceId"].split(":")[-1] for v in entries} ==
               {c["ID"] for c in containers if c["Service"]==s} for s,entries in registry.items())
    e.evidence("etapa6-03-replicas-docker.json",containers)
    e.evidence("etapa6-04-eureka-2-2-2.json",registry)
def routing():
    admin,financial=tokens()
    old=json.loads((ROOT/".local/etapa4-state.json").read_text(encoding="utf-8"))
    paths={"customer-service":"/api/customers/"+old["customerId"],
           "account-service":"/api/accounts/"+str(old["source"]),
           "payment-service":"/api/payments/operations/"+old["deposit"]["operationId"]}
    registry=instances()
    for service,path in paths.items():
        requests=[request(service,"GET",path,financial if service=="payment-service" else admin,expected=200,replicas=registry) for _ in range(8)]
        assert len({r["connectedIp"] for r in requests})==2
        access=[]
        for c in docker():
            if c["Service"]==service:
                wait(lambda:any("GET "+path+" 200" in line for line in
                    e.run(["docker","exec",c["ID"],"sh","-c","cat /tmp/banco-access/*"]).splitlines()),timeout=35)
                logs=e.run(["docker","exec",c["ID"],"sh","-c","cat /tmp/banco-access/*"])
                matching=[line for line in logs.splitlines() if "GET "+path+" 200" in line]
                assert matching,"Sin evidencia de atención: "+c["Name"]
                access.append({"container":c["Name"],"requests":matching[-8:]})
        e.evidence("etapa6-05-routing-"+service+".json",{"mechanism":"Eureka UP + round-robin runner + curl --resolve (DNS TLS identity, selected replica IP)","requests":requests,"containerAccessLogs":access})
def flow():
    admin,financial=tokens()
    assert not STATE.exists(),"Estado ya existente: no repetir fixtures automáticamente"
    s={"runId":"eft6-"+uuid.uuid4().hex[:12],"customerId":str(uuid.uuid4()),
       "source":int(time.time()*1000),"target":int(time.time()*1000)+1}
    save(s)
    records=[request("customer-service","PUT","/api/customers/"+s["customerId"],admin,
                     {"name":"Prueba escalabilidad EFT 6"},expected=201)]
    for id in [s["source"],s["target"]]:
        records.append(request("account-service","PUT",f"/api/accounts/{id}",admin,
                        {"accountType":"ahorro","customerIds":[s["customerId"]]},expected=201))
    denied=[
        request("customer-service","GET","/api/customers/"+s["customerId"],expected=401),
        request("payment-service","POST","/api/payments/deposits",admin,
            {"accountId":s["source"],"amount":1},s["runId"]+"-denied",403)]
    e.evidence("etapa6-07b-oauth-401-403.json",denied)
    replays=[]
    for kind,path,body in [
        ("deposit","/api/payments/deposits",{"accountId":s["source"],"amount":100}),
        ("transfer","/api/payments/transfers",{"sourceAccountId":s["source"],"targetAccountId":s["target"],"amount":20}),
        ("payment","/api/payments",{"sourceAccountId":s["source"],"amount":1})]:
        key=s["runId"]+"-"+kind
        result=request("payment-service","POST",path,financial,body,key,201)
        assert result["response"]["status"]=="COMPLETED"
        records.append(result)
        s[kind]={"key":key,"path":path,"body":body,"operationId":result["response"]["operationId"]};save(s)
        replay=request("payment-service","POST",path,financial,body,key,200)
        assert replay["response"]==result["response"]
        replays.append(replay)
    conflict=request("payment-service","POST","/api/payments/deposits",financial,
             {"accountId":s["source"],"amount":101},s["deposit"]["key"],409)
    def deposit(n):
        return request("payment-service","POST","/api/payments/deposits",financial,
            {"accountId":s["source"],"amount":1},s["runId"]+"-burst-"+str(n),201)
    registry=instances()
    # Every Payment handles traffic; its @LoadBalanced client independently chooses Account.
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
        burst=list(executor.map(deposit,range(20)))
    assert all(r["response"]["status"]=="COMPLETED" for r in burst)
    s["expectedSource"]=99;s["expectedTarget"]=20;s["postings"]=23;save(s)
    e.evidence("etapa6-06-flujo-financiero-escalado.json",{"runId":s["runId"],"records":records,"concurrentDeposits":burst})
    e.evidence("etapa6-07-idempotencia.json",{"replays":replays,"payloadConflict":conflict,
        "uniquePostingKeys":e.scalar("SELECT COUNT(*) FROM eft_account_posting WHERE idempotency_key LIKE '"+s["runId"]+"%'"),
        "uniquePaymentKeys":e.scalar("SELECT COUNT(*) FROM eft_payment_operation WHERE idempotency_key LIKE '"+s["runId"]+"%'")})
def financial_snapshot():
    s=state();prefix=s["runId"]
    outboxes=json.loads(e.scalar("SELECT COALESCE(json_agg(row_to_json(x)),'[]'::json) FROM "
        "(SELECT o.* FROM eft_financial_outbox o JOIN eft_account_posting p USING(operation_id) WHERE p.idempotency_key LIKE '"+prefix+"%' ORDER BY event_id) x"))
    audits=int(e.scalar("SELECT COUNT(*) FROM eft_payment_event_audit a JOIN eft_account_posting p USING(operation_id) WHERE p.idempotency_key LIKE '"+prefix+"%'"))
    balances=json.loads(e.scalar("SELECT json_agg(row_to_json(b)) FROM eft_account_balance b WHERE account_id IN ("+str(s["source"])+","+str(s["target"])+")"))
    return {"outboxes":outboxes,"audits":audits,"balances":balances}
def group():
    return e.run(["docker","exec","banco-legacy-kafka-1","/opt/kafka/bin/kafka-consumer-groups.sh",
        "--bootstrap-server","localhost:9092","--describe","--group","financial-payment-audit"],timeout=60)
def outbox():
    s=state()
    wait(lambda:len(financial_snapshot()["outboxes"])==s["postings"] and
         all(o["status"]=="PUBLISHED" for o in financial_snapshot()["outboxes"]) and financial_snapshot()["audits"]==s["postings"])
    snap=financial_snapshot()
    assert len({o["claim_owner"] for o in snap["outboxes"]})==2,"No participaron ambos publishers"
    assert all(o["attempts"]==1 and o["published_at"] and o["lease_until"] is None for o in snap["outboxes"])
    actual={b["account_id"]:b["balance"] for b in snap["balances"]}
    assert actual[s["source"]]==s["expectedSource"] and actual[s["target"]]==s["expectedTarget"]
    # Broker redelivery of the same eventId proves deduplication across Payment replicas.
    payload=snap["outboxes"][0]["payload"];event=json.loads(payload)
    before=snap["audits"]
    e.run(["docker","exec","-i","banco-legacy-kafka-1","/opt/kafka/bin/kafka-console-producer.sh",
        "--bootstrap-server","localhost:9092","--topic",e.TOPIC,"--property","parse.key=true"],
        input=event["result"]["operationId"]+"\t"+payload+"\n",timeout=60)
    wait(lambda:group_lag_zero(group()))
    assert financial_snapshot()["audits"]==before
    assignment=group()
    rows=[line.split() for line in assignment.splitlines() if line.startswith("financial-payment-audit ")]
    assert len(rows)==3 and len({row[-3] for row in rows})==2,assignment
    assert all(int(row[5])==0 for row in rows)
    e.evidence("etapa6-08-outbox-coordinada.json",{"sqlReadOnly":True,"snapshot":snap,"logicalAuditDeduplication":True,"brokerReplayEventId":event["eventId"]})
    e.evidence("etapa6-09-consumer-group-payment.json",{"describe":assignment,"consumers":e.run(["docker","exec","banco-legacy-kafka-1","/opt/kafka/bin/kafka-consumer-groups.sh","--bootstrap-server","localhost:9092","--describe","--group","financial-payment-audit","--members","--verbose"],timeout=60),"partitions":3,"distinctMembers":2,"finalLag":0})
    # Prove existing Spring LoadBalancer callers reached each individual replica.
    calls={}
    for c in docker():
        if c["Service"] in SERVICES:
            logs=e.run(["docker","exec",c["ID"],"sh","-c","cat /tmp/banco-access/*"])
            lines=[l for l in logs.splitlines() if
                   (c["Service"]=="account-service" and "POST /internal/accounts/postings 201" in l) or
                   (c["Service"]=="customer-service" and "GET /api/customers/"+s["customerId"]+" 200" in l)]
            calls[c["Name"]]=lines
    assert all(calls[c["Name"]] for c in docker() if c["Service"]=="account-service"),calls
    e.evidence("etapa6-08b-routing-interservicios-loadbalancer.json",calls)
def group_lag_zero(output):
    rows=[l.split() for l in output.splitlines() if l.startswith("financial-payment-audit ")]
    return len(rows)==3 and all(row[5].isdigit() and int(row[5])==0 for row in rows)
def failover():
    s=state()
    for service in SERVICES:
        admin,financial=tokens()
        selected=next(c for c in docker() if c["Service"]==service and c["Health"]=="healthy")
        started=time.monotonic()
        try:
            e.run(["docker","stop",selected["ID"]],timeout=60)
            wait(lambda:len(instances()[service])==1,timeout=100)
            paths={"customer-service":"/api/customers/"+s["customerId"],
                   "account-service":"/api/accounts/"+str(s["source"]),
                   "payment-service":"/api/payments/operations/"+s["deposit"]["operationId"]}
            result=request(service,"GET",paths[service],financial if service=="payment-service" else admin,expected=200)
            # A real operation while one Account/Payment remains, without touching previous fixtures.
            operation=None
            if service in ["account-service","payment-service"]:
                operation=request("payment-service","POST","/api/payments/deposits",financial,
                    {"accountId":s["source"],"amount":1},s["runId"]+"-failover-"+service)
                assert operation["status"] in [200,201],operation
                assert operation["response"]["status"]=="COMPLETED"
                if service not in s.get("failoverOperations",[]):
                    s["expectedSource"]+=1;s["postings"]+=1
                    s.setdefault("failoverOperations",[]).append(service)
                save(s)
            consumer=None
            if service=="payment-service":
                wait(lambda:group_lag_zero(group()),timeout=90)
                consumer=group()
                rows=[line.split() for line in consumer.splitlines() if line.startswith("financial-payment-audit ")]
                assert len({row[-3] for row in rows})==1,consumer
            e.evidence("etapa6-10-failover-"+service+".json",{"stoppedContainer":selected,
                "discoveryConvergenceSeconds":round(time.monotonic()-started,2),
                "survivingInstances":instances()[service],"response":result,"financialOperation":operation,"consumerGroup":consumer})
        finally:
            e.run(["docker","start",selected["ID"]],timeout=60)
        wait(lambda:len(instances()[service])==2)
        wait(lambda:sum(c["Service"]==service and c["Health"]=="healthy" for c in docker())==2)
    wait(lambda:financial_snapshot()["audits"]==s["postings"] and all(o["status"]=="PUBLISHED" for o in financial_snapshot()["outboxes"]))
    e.evidence("etapa6-10b-recuperacion-replicas.json",{"docker":docker(),"eureka":instances(),"financial":financial_snapshot()})
def bff_final(admin):
    spec=importlib.util.spec_from_file_location("bff",Path(__file__).with_name("validate-bff-tls.py"))
    bff=importlib.util.module_from_spec(spec);spec.loader.exec_module(bff)
    tokens={c:e.token("banco-"+c+"-bff","OAUTH_"+c.upper()+"_CLIENT_SECRET","accounts."+c,"etapa6-"+c) for c in bff.CHANNELS}
    results={}
    for channel,(port,endpoint) in bff.CHANNELS.items():
        path=f"/api/{channel}/accounts/101/{endpoint}"
        result=bff.request(port,path,tokens[channel]);assert result["status"]==200,result
        previous=json.loads((e.EVIDENCE/f"etapa5-03-{channel}-200.json").read_text(encoding="utf-8"))["data"]["request"]["response"]
        assert result["response"]==previous,"Regresión de contrato legacy: "+channel
        no_token=bff.request(port,path);assert no_token["status"]==401
        wrong=bff.request(port,path,tokens["mobile" if channel=="web" else "web"]);assert wrong["status"]==403
        certificate=bff.served(port,bff.CERT)
        assert certificate["sha256"]==bff.certificate(bff.CERT)["sha256"]
        results[channel]={"success":result,"withoutToken":no_token,"wrongRole":wrong,
                          "servedCertificate":certificate,"identicalToStage5Contract":True}
    e.evidence("etapa6-16-bff-legacy-regresion-final.json",results)
    restored=bff.request(8085,"/api/accounts/"+str(state()["source"]),admin)
    assert restored["status"]==200,restored
    e.evidence("etapa6-17-account-host-8085-base.json",restored)
def final():
    admin,financial=tokens();s=state()
    wait(lambda:all(len(v)==1 for v in instances().values()))
    responses=[
        request("customer-service","GET","/api/customers/"+s["customerId"],admin,expected=200),
        request("account-service","GET","/api/accounts/"+str(s["source"]),admin,expected=200),
        request("payment-service","GET","/api/payments/operations/"+s["deposit"]["operationId"],financial,expected=200)]
    preserved=json.loads((ROOT/".local/etapa6-preserved.json").read_text(encoding="utf-8"))
    summary={}
    for table,original in preserved.items():
        current=json.loads(e.scalar(f"SELECT COALESCE(json_agg(row_to_json(t)),'[]'::json) FROM {table} t"))
        def fingerprint(row,keys): return json.dumps({k:row[k] for k in keys},sort_keys=True)
        keys=list(original[0]) if original else []
        old=Counter(fingerprint(r,keys) for r in original);now=Counter(fingerprint(r,keys) for r in current)
        assert old<=now,"Datos anteriores alterados: "+table
        summary[table]={"beforeRows":len(original),"afterRows":len(current),"previousRowsPreserved":True}
    snap=financial_snapshot()
    balances={r["account_id"]:r["balance"] for r in snap["balances"]}
    assert balances[s["source"]]==s["expectedSource"] and balances[s["target"]]==s["expectedTarget"]
    assert snap["audits"]==s["postings"] and all(o["status"]=="PUBLISHED" for o in snap["outboxes"])
    wait(lambda:group_lag_zero(group()))
    containers=docker()
    assert all(c["Health"]=="healthy" for c in containers)
    assert all(sum(c["Service"]==service for c in containers)==1 for service in SERVICES)
    e.evidence("etapa6-11-postgresql-final.json",{"sqlReadOnly":True,"preserved":summary,"run":s,"financial":snap})
    infra=json.loads((ROOT/".local/etapa6-infra.json").read_text(encoding="utf-8"))
    assert all(any(c["ID"]==id and c["Service"]==service for c in containers) for service,id in infra.items())
    volume=e.run(["docker","inspect","--format",'{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}',"banco-legacy-postgres-1"]).strip()
    assert volume==json.loads((e.EVIDENCE/"etapa6-01-preservacion-previa.json").read_text(encoding="utf-8"))["data"]["postgresVolume"]
    bff_final(admin)
    e.evidence("etapa6-12-compose-final.json",{"infrastructureIdsPreserved":True,"postgresVolumePreserved":volume,"containers":containers,"eureka":instances(),"modernResponses":responses,"kafkaConsumerGroup":group()})
if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("phase",choices=["config","ready","routing","flow","outbox","failover","final"])
    args=parser.parse_args();globals()[args.phase]()
