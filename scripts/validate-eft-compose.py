"""Validación EFT real: usa APIs HTTPS y SQL read-only; evidencia sin tokens/secretos.
Ejecutar desde raíz: python scripts/validate-eft-compose.py baseline|resilience|kafka
resilience detiene solo Account y lo restaura; kafka reproduce un evento y envía JSON inválido identificado.
No inicializa servicios, elimina datos ni modifica .env. Requiere entorno Compose preparado.
"""
from pathlib import Path
import sys
import argparse, base64, datetime, decimal, json, ssl, subprocess, time, urllib.request, urllib.parse, urllib.error, uuid

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "docs/evidence/eft"
STATE = ROOT / ".local/etapa4-state.json"
TOPIC = "banco.operaciones.completadas.v1"
PORTS = {"customer-service":8087, "account-service":8085, "payment-service":8086}
ENV = {}
for line in (ROOT / ".env").read_text(encoding="utf-8-sig").splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        key,value=line.split("=",1)
        ENV[key.strip()]=value.strip().strip('"').strip("'")

def run(args, input=None, timeout=45):
    result=subprocess.run(args, input=input, capture_output=True, text=True, encoding="utf-8", errors="replace", cwd=ROOT, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f"{args[0]} exit {result.returncode}: "+redact(result.stderr))
    return result.stdout

def redact(value):
    for key,secret in ENV.items():
        if secret and any(part in key for part in ["SECRET","PASSWORD","KEY"]):
            value=value.replace(secret,"[REDACTED]")
    return value

def safe(value):
    if isinstance(value,dict):
        return {k:("[REDACTED]" if k.lower() in ("access_token","refresh_token","authorization","client_secret") else safe(v)) for k,v in value.items()}
    if isinstance(value,list): return [safe(v) for v in value]
    if isinstance(value,str): return redact(value)
    return value

def evidence(name,data):
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    output={"capturedAt":datetime.datetime.now(datetime.timezone.utc).isoformat(),"data":safe(data)}
    (EVIDENCE / name).write_text(json.dumps(output,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print("Evidencia: "+name,flush=True)

def sql(query):
    output=run(["docker","compose","exec","-T","postgres","psql","-X","-v","ON_ERROR_STOP=1","-U","postgres","-d","banco_legacy_batch","-tA","-c","BEGIN READ ONLY; "+query+"; COMMIT;"])
    return "\n".join(line for line in output.splitlines() if line not in ["BEGIN","COMMIT"])

def scalar(query):
    return sql(query).strip()

def token(client,secret_key,scopes,label):
    context=ssl.create_default_context(cafile=str(ROOT/".local/compose/banco-legacy-docker.crt"))
    data=urllib.parse.urlencode({"grant_type":"client_credentials","scope":scopes}).encode()
    auth=base64.b64encode((client+":"+ENV[secret_key]).encode()).decode()
    request=urllib.request.Request("https://localhost:8084/oauth2/token",data=data,headers={"Authorization":"Basic "+auth,"Content-Type":"application/x-www-form-urlencoded"})
    with urllib.request.urlopen(request,context=context,timeout=15) as response:
        body=json.load(response);status=response.status
    evidence("04-oauth-"+label+".json",{"status":status,"client":client,"requestedScopes":scopes,"response":body,"tlsCertificateAndHostnameVerified":True})
    assert status==200
    return body["access_token"]

def tokens():
    return (token("banco-domain-operator","OAUTH_DOMAIN_CLIENT_SECRET","accounts.read accounts.write customers.read customers.write","domain"),
            token("banco-payment-operator","OAUTH_PAYMENT_CLIENT_SECRET","payments.read payments.write accounts.post accounts.post.read","payment"))

def http(service,method,path,token_value=None,body=None,key=None,expected=None):
    config={"url":f"https://{service}:{PORTS[service]}{path}","request":method,"cacert":"/run/banco-legacy/banco-legacy-docker.crt","max-time":"15","connect-timeout":"5","write-out":"\\n%{http_code}"}
    lines=["silent","show-error"]+[k+" = "+json.dumps(v) for k,v in config.items()]
    if token_value:lines.append("header = "+json.dumps("Authorization: Bearer "+token_value))
    if key:lines.append("header = "+json.dumps("Idempotency-Key: "+key))
    if body is not None:
        lines.append("header = "+json.dumps("Content-Type: application/json"))
        lines.append("data = "+json.dumps(json.dumps(body,separators=(",",":"))))
    output=run(["docker","compose","exec","-T","payment-service","curl","--config","-"],input="\n".join(lines)+"\n",timeout=25)
    payload,status=output.rsplit("\n",1);status=int(status)
    parsed=json.loads(payload) if payload.strip() else None
    record={"method":method,"url":config["url"],"idempotencyKey":key,"request":body,"status":status,"response":parsed,"tlsCertificateAndHostnameVerified":True}
    if expected is not None and status!=expected:
        evidence("error-http.json",record)
        raise AssertionError(f"{method} {path}: {status}, esperado {expected}: {safe(parsed)}")
    return record

def response(record):return record["response"]
def account(id,admin):return response(http("account-service","GET",f"/api/accounts/{id}",admin,expected=200))
def balance(id,admin):return decimal.Decimal(str(account(id,admin)["balance"]))
def wait(condition,seconds=65):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        if condition():return
        time.sleep(2)
    raise AssertionError("Se agotó espera de condición")
def save(state): STATE.write_text(json.dumps(state,indent=2),encoding="utf-8")

def baseline():
    admin,financial=tokens()
    state={"runId":"eft4-"+uuid.uuid4().hex[:12],"customerId":str(uuid.uuid4()),"source":int(time.time()*1000),"target":int(time.time()*1000)+1}
    save(state)
    uri="/api/customers/"+state["customerId"]
    created=http("customer-service","PUT",uri,admin,{"name":"Prueba EFT 4"},expected=201)
    queried=http("customer-service","GET",uri,admin,expected=200)
    updated=http("customer-service","PATCH",uri,admin,{"name":"Prueba EFT 4 actualizada","version":0},expected=200)
    assert response(updated)["version"]==1
    evidence("05-customer.json",[created,queried,updated,{"postgres":json.loads(scalar(f"SELECT row_to_json(c) FROM eft_customer c WHERE customer_id='{state['customerId']}'"))}])
    openings=[]
    for id in [state["source"],state["target"]]:
        record=http("account-service","PUT",f"/api/accounts/{id}",admin,{"accountType":"ahorro","customerIds":[state["customerId"]]},expected=201)
        value=response(record)
        assert value["status"]=="ACTIVE" and decimal.Decimal(str(value["balance"]))==0 and value["customerId"]==state["customerId"]
        openings.append(record)
    listing=http("account-service","GET","/api/accounts?customerId="+state["customerId"],admin,expected=200)
    assert len(response(listing))==2
    maintenance=http("account-service","PATCH",f"/api/accounts/{state['source']}",admin,{"accountType":"ahorro","version":0},expected=200)
    assert response(maintenance)["version"]==1
    evidence("06-account-apertura.json",openings+[listing,maintenance])
    records=[]
    for kind,path,body in [
        ("deposit","/api/payments/deposits",{"accountId":state["source"],"amount":100}),
        ("transfer","/api/payments/transfers",{"sourceAccountId":state["source"],"targetAccountId":state["target"],"amount":30}),
        ("payment","/api/payments",{"sourceAccountId":state["target"],"amount":10})]:
        key=state["runId"]+"-"+kind
        record=http("payment-service","POST",path,financial,body,key,201)
        assert response(record)["status"]=="COMPLETED"
        state[kind]={"key":key,"operationId":response(record)["operationId"],"path":path,"body":body}
        save(state);records.append(record)
        evidence("07-payment-"+kind+".json",record)
    assert balance(state["source"],admin)==70 and balance(state["target"],admin)==20
    before=scalar(f"SELECT COUNT(*) FROM eft_account_posting WHERE actor='banco-payment-operator' AND idempotency_key LIKE '{state['runId']}%'")
    replays=[]
    for kind in ["deposit","transfer","payment"]:
        op=state[kind]
        replay=http("payment-service","POST",op["path"],financial,op["body"],op["key"],200)
        assert response(replay)==response(records[["deposit","transfer","payment"].index(kind)])
        replays.append(replay)
    conflict=http("payment-service","POST",state["deposit"]["path"],financial,{"accountId":state["source"],"amount":101},state["deposit"]["key"],409)
    after=scalar(f"SELECT COUNT(*) FROM eft_account_posting WHERE actor='banco-payment-operator' AND idempotency_key LIKE '{state['runId']}%'")
    assert before==after=="3"
    operations=scalar(f"SELECT COUNT(*) FROM eft_payment_operation WHERE actor='banco-payment-operator' AND idempotency_key LIKE '{state['runId']}%'")
    assert operations=="3"
    assert balance(state["source"],admin)==70 and balance(state["target"],admin)==20
    evidence("08-idempotencia.json",{"replays":replays,"conflict":conflict,"postingsBefore":before,"postingsAfter":after,"paymentOperations":operations,"sourceBalance":70,"targetBalance":20})
    denied=[http("payment-service","POST","/api/payments/deposits",None,{"accountId":state["source"],"amount":1},state["runId"]+"-no-token",401),
            http("payment-service","POST","/api/payments/deposits",admin,{"accountId":state["source"],"amount":1},state["runId"]+"-wrong-scope",403)]
    evidence("09-seguridad.json",denied)
    target=account(state["target"],admin)
    closed=http("account-service","POST",f"/api/accounts/{state['target']}/closure",admin,{"version":target["version"]},expected=200)
    assert response(closed)["status"]=="CLOSED" and decimal.Decimal(str(response(closed)["balance"]))==20
    rejected=http("payment-service","POST","/api/payments/deposits",financial,{"accountId":state["target"],"amount":1},state["runId"]+"-closed",409)
    assert response(rejected)["code"]=="ACCOUNT_CLOSED"
    rollback=http("payment-service","POST","/api/payments/transfers",financial,{"sourceAccountId":state["source"],"targetAccountId":state["target"],"amount":5},state["runId"]+"-rollback",409)
    assert balance(state["source"],admin)==70 and balance(state["target"],admin)==20
    preserved=scalar(f"SELECT COUNT(*) FROM eft_account WHERE account_id={state['target']} AND status='CLOSED'")
    assert preserved=="1"
    evidence("10-cierre-rollback.json",{"closure":closed,"rejectedDeposit":rejected,"rejectedTransfer":rollback,"recordPreserved":True,"balances":[70,20]})
    print("PASS: Customer, Account, Payment, idempotencia, seguridad, cierre y rollback reales.",flush=True)

def resilience():
    state=json.loads(STATE.read_text());admin,financial=tokens()
    normal=http("payment-service","GET","/api/payments/operations/"+state["deposit"]["operationId"],financial,expected=200)
    key=state["runId"]+"-recovery";body={"accountId":state["source"],"amount":7}
    stopped=False
    try:
        run(["docker","compose","stop","account-service"]);stopped=True
        failed=http("payment-service","POST","/api/payments/deposits",financial,body,key,503)
        repeated=[http("payment-service","POST","/api/payments/deposits",financial,body,key,503) for _ in range(3)]
        event_key=state["runId"]+"-event-recovery"
        event_failed=http("payment-service","POST","/api/payments/deposits",financial,{"accountId":state["source"],"amount":2},event_key,503)
        event_pending=json.loads(scalar("SELECT request FROM eft_payment_operation WHERE actor='banco-payment-operator' AND idempotency_key='"+event_key+"'"))
        state["eventRecovery"]={"key":event_key,"request":event_pending};save(state)
        pending=json.loads(scalar("SELECT row_to_json(p) FROM (SELECT operation_id,status,receipt FROM eft_payment_operation WHERE actor='banco-payment-operator' AND idempotency_key='"+key+"') p"))
        assert pending["status"]=="PENDING" and pending["receipt"] is None
        assert scalar("SELECT COUNT(*) FROM eft_account_posting WHERE actor='banco-payment-operator' AND idempotency_key='"+key+"'")=="0"
        state["recovery"]={"key":key,"operationId":pending["operation_id"]};save(state)
        evidence("11-resilience-503.json",{"normal200":normal,"accountStopped":True,"response503":failed,"repeated503":repeated,"eventRecovery503":event_failed,"postgresPending":pending,"postings":0})
    finally:
        if stopped:run(["docker","compose","up","-d","--no-deps","account-service"],timeout=60)
    wait(lambda: "healthy"==run(["docker","inspect","banco-legacy-account-service-1","--format","{{.State.Health.Status}}"]).strip(),100)
    # Allow refreshed Eureka instances and circuit HALF_OPEN after outage.
    recovered=None;attempts=[]
    for i in range(35):
        result=http("payment-service","POST","/api/payments/deposits",financial,body,key)
        attempts.append({"attempt":i+1,"status":result["status"]})
        if result["status"]==200: recovered=result;break
        assert result["status"]==503,result
        time.sleep(3)
    assert recovered is not None,"Account healthy pero aún sin recuperación"
    replay=http("payment-service","POST","/api/payments/deposits",financial,body,key,200)
    assert response(replay)==response(recovered)
    assert balance(state["source"],admin)==77
    count=scalar("SELECT COUNT(*) FROM eft_account_posting WHERE actor='banco-payment-operator' AND idempotency_key='"+key+"'")
    assert count=="1"
    evidence("12-resilience-recuperacion.json",{"recovered":recovered,"replay":replay,"attempts":attempts,"sourceBalance":77,"postings":1})
    event_op=state["eventRecovery"]
    command=http("account-service","POST","/internal/accounts/postings",financial,event_op["request"],event_op["key"],201)
    operation_id=event_op["request"]["operationId"]
    wait(lambda:scalar("SELECT status FROM eft_payment_operation WHERE operation_id='"+operation_id+"'")=="COMPLETED",90)
    event_replay=http("payment-service","POST","/api/payments/deposits",financial,{"accountId":state["source"],"amount":2},event_op["key"],200)
    assert response(event_replay)["operationId"]==operation_id
    assert balance(state["source"],admin)==79
    event_count=scalar("SELECT COUNT(*) FROM eft_account_posting WHERE operation_id='"+operation_id+"'")
    assert event_count=="1"
    evidence("12b-kafka-reconciliacion-pending.json",{"pendingCreatedWhileAccountStopped":True,"accountInternalCommand":command,"paymentReconciledBeforePostRetry":True,"replay":event_replay,"sourceBalance":79,"postings":1})
    print("PASS: caída Account→503/PENDING, recuperación por HTTP/evento y replay sin duplicar saldo.",flush=True)

def kafka():
    state=json.loads(STATE.read_text());prefix=state["runId"]
    query="SELECT COUNT(*) FROM eft_financial_outbox o JOIN eft_account_posting p ON p.operation_id=o.operation_id WHERE p.actor='banco-payment-operator' AND p.idempotency_key LIKE '"+prefix+"%'"
    wait(lambda:scalar(query+" AND o.status='PUBLISHED'")=="5",90)
    audit_query="SELECT COUNT(*) FROM eft_payment_event_audit a JOIN eft_account_posting p ON p.operation_id=a.operation_id WHERE p.actor='banco-payment-operator' AND p.idempotency_key LIKE '"+prefix+"%'"
    wait(lambda:scalar(audit_query)=="5",90)
    rows=json.loads(scalar("SELECT COALESCE(json_agg(x),'[]'::json) FROM (SELECT o.event_id,o.operation_id,o.status,o.attempts,o.published_at FROM eft_financial_outbox o JOIN eft_account_posting p ON p.operation_id=o.operation_id WHERE p.idempotency_key LIKE '"+prefix+"%' ORDER BY o.operation_id) x"))
    describe=run(["docker","compose","exec","-T","kafka","/opt/kafka/bin/kafka-topics.sh","--bootstrap-server","localhost:9092","--describe","--topic",TOPIC])
    operation=state["deposit"]["operationId"]
    event=json.loads(scalar("SELECT payload FROM eft_financial_outbox WHERE operation_id='"+operation+"'"))
    run(["docker","compose","exec","-T","kafka","/opt/kafka/bin/kafka-console-producer.sh","--bootstrap-server","localhost:9092","--topic",TOPIC,"--property","parse.key=true","--property","key.separator=|"],input=operation+"|"+json.dumps(event,separators=(",",":"))+"\n")
    time.sleep(5)
    assert scalar(audit_query)=="5"
    assert scalar("SELECT COUNT(*) FROM eft_payment_event_audit WHERE event_id='"+event["eventId"]+"'")=="1"
    group=run(["docker","compose","exec","-T","kafka","/opt/kafka/bin/kafka-consumer-groups.sh","--bootstrap-server","localhost:9092","--describe","--group","financial-payment-audit"])
    evidence("13-kafka-outbox-audit.json",{"topic":TOPIC,"topicDescription":describe,"outbox":rows,"auditCount":5,"replayedEventId":event["eventId"],"replayedEventAuditCount":1,"consumerGroup":group})
    poison_key=prefix+"-poison"
    run(["docker","compose","exec","-T","kafka","/opt/kafka/bin/kafka-console-producer.sh","--bootstrap-server","localhost:9092","--topic",TOPIC,"--property","parse.key=true","--property","key.separator=|"],input=poison_key+"|{invalid-json\n")
    dlt_result=subprocess.run(["docker","compose","exec","-T","kafka","/opt/kafka/bin/kafka-console-consumer.sh","--bootstrap-server","localhost:9092","--topic",TOPIC+".DLT","--from-beginning","--timeout-ms","12000","--max-messages","1000","--property","print.key=true"],capture_output=True,text=True,encoding="utf-8",errors="replace",cwd=ROOT,timeout=25)
    if dlt_result.returncode and "TimeoutException" not in dlt_result.stderr:
        raise RuntimeError("Error de consumidor DLT: "+redact(dlt_result.stderr))
    dlt="\n".join(line for line in dlt_result.stdout.splitlines() if poison_key in line)
    assert poison_key in dlt
    evidence("14-kafka-dlt.json",{"injectedKey":poison_key,"dltTopic":TOPIC+".DLT","dltOutput":dlt,"malformedJsonIsNonRetryable":True,"transientRetriesCoveredByTests":True})
    admin,financial=tokens()
    op=state["payment"]
    follow=http("payment-service","POST",op["path"],financial,op["body"],op["key"],200)
    assert response(follow)["status"]=="COMPLETED"
    run(["docker","compose","exec","-T","kafka","/opt/kafka/bin/kafka-console-producer.sh","--bootstrap-server","localhost:9092","--topic",TOPIC,"--property","parse.key=true","--property","key.separator=|"],input=operation+"|"+json.dumps(event,separators=(",",":"))+"\n")
    def caught_up():
        status=run(["docker","compose","exec","-T","kafka","/opt/kafka/bin/kafka-consumer-groups.sh","--bootstrap-server","localhost:9092","--describe","--group","financial-payment-audit"])
        rows=[line.split() for line in status.splitlines() if line.strip().startswith("financial-payment-audit ")]
        return len(rows)==3 and all(row[5]=="0" for row in rows)
    wait(caught_up,30)
    assert scalar(audit_query)=="5"
    evidence("15-kafka-continuidad.json",{"paymentReplay":follow,"validEventRepublishedAfterDlt":event["eventId"],"allPartitionsLagZero":True,"auditCountStill":5})
    snapshot=json.loads(scalar("SELECT json_build_object('customer',(SELECT row_to_json(c) FROM eft_customer c WHERE customer_id='"+state["customerId"]+"'),'accounts',(SELECT json_agg(x) FROM (SELECT a.account_id,a.status,a.version,b.balance FROM eft_account a JOIN eft_account_balance b USING(account_id) WHERE a.account_id IN ("+str(state["source"])+","+str(state["target"])+")) x),'payments',(SELECT json_agg(x) FROM (SELECT operation_id,status,idempotency_key,failure_code FROM eft_payment_operation WHERE idempotency_key LIKE '"+prefix+"%') x),'postings',(SELECT COUNT(*) FROM eft_account_posting WHERE idempotency_key LIKE '"+prefix+"%'),'audit',(SELECT COUNT(*) FROM eft_payment_event_audit a JOIN eft_account_posting p USING(operation_id) WHERE p.idempotency_key LIKE '"+prefix+"%'))"))
    evidence("16-postgresql-final.json",snapshot)
    print("PASS: broker Compose, outbox PUBLISHED, auditoría, dedup y DLT reales.",flush=True)

if __name__=="__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("phase",choices=["baseline","resilience","kafka"])
    phase=parser.parse_args().phase
    {"baseline":baseline,"resilience":resilience,"kafka":kafka}[phase]()
