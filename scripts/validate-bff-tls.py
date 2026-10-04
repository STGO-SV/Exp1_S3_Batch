"""BFF legacy: diagnóstico TLS, regresión por canal y fallback real.
python -B scripts/validate-bff-tls.py diagnose|regression|resilience
Requiere Compose preparado. No genera certificados; resilience restaura Account en finally.
"""
import argparse, base64, hashlib, importlib.util, json, socket, ssl, time, urllib.request, urllib.error
from pathlib import Path
spec=importlib.util.spec_from_file_location("eft",Path(__file__).with_name("validate-eft-compose.py"))
eft=importlib.util.module_from_spec(spec);spec.loader.exec_module(eft)
ROOT=eft.ROOT
CERT=ROOT/".local/compose/banco-legacy-docker.crt"
CHANNELS={"web":(8081,"dashboard"),"mobile":(8082,"summary"),"atm":(8083,"balance")}
def inventory():
    result={}
    for line in eft.run(["docker","compose","ps","--format","json"]).splitlines():
        v=json.loads(line)
        record={k:v[k] for k in ["ID","Image","State","Health"]}
        record["imageId"]=eft.run(["docker","inspect","--format","{{.Image}}",v["ID"]]).strip()
        record["tlsMount"]=eft.run(["docker","inspect","--format",'{{range .Mounts}}{{if eq .Destination "/run/banco-legacy"}}{{.Source}} -> {{.Destination}} (RW={{.RW}}){{end}}{{end}}',v["ID"]]).strip()
        result[v["Service"]]=record
    return result
def certificate(path):
    pem=path.read_text(encoding="utf-8")
    return {"sha256":hashlib.sha256(ssl.PEM_cert_to_DER_cert(pem)).hexdigest(),
            **ssl._ssl._test_decode_cert(str(path))}
def served(port,trust):
    context=ssl.create_default_context(cafile=str(trust))
    with socket.create_connection(("localhost",port),timeout=10) as raw:
        with context.wrap_socket(raw,server_hostname="localhost") as conn:
            return {"sha256":hashlib.sha256(conn.getpeercert(binary_form=True)).hexdigest(),
                    **conn.getpeercert(),"protocol":conn.version(),"hostnameVerified":True}
def diagnose():
    records={"containers":inventory(),"currentMaterial":certificate(CERT),"bff":{}}
    old=ROOT/".local/etapa4-cert-anterior.pem"
    for channel,(port,_) in CHANNELS.items():
        try:
            peer=served(port,CERT);current=True;error=None
        except ssl.SSLCertVerificationError as exc:
            current=False;error=str(exc)
            peer=served(port,old) # Inspección verificada con el certificado público anterior.
        records["bff"][channel]={"servedCertificate":peer,"trustedByCurrentMaterial":current,"currentVerificationError":error}
    eft.evidence("etapa5-01-diagnostico-tls-antes.json",records)
def request(port,path,token=None):
    headers={"Authorization":"Bearer "+token} if token else {}
    req=urllib.request.Request(f"https://localhost:{port}{path}",headers=headers)
    try:
        resp=urllib.request.urlopen(req,context=ssl.create_default_context(cafile=str(CERT)),timeout=20)
    except urllib.error.HTTPError as exc:resp=exc
    with resp:
        raw=resp.read().decode()
        try:body=json.loads(raw)
        except ValueError:body=raw
        return {"url":req.full_url,"status":resp.status,"response":body,
                "certificateAndHostnameVerified":True}
def tokens():
    return {c:eft.token("banco-"+c+"-bff","OAUTH_"+c.upper()+"_CLIENT_SECRET",
                       "accounts."+c,"etapa5-"+c) for c in CHANNELS}
def legacy():
    return int(eft.scalar("SELECT cuenta_id FROM interes_procesado ORDER BY cuenta_id LIMIT 1"))
def regression():
    t=tokens();account=legacy()
    current=inventory()
    initial=json.loads((eft.EVIDENCE/"etapa5-01-diagnostico-tls-antes.json").read_text(encoding="utf-8"))["data"]
    assert all(v["ID"]==current[s]["ID"] for s,v in initial["containers"].items() if s not in [c+"-bff" for c in CHANNELS])
    assert certificate(CERT)["sha256"]==initial["currentMaterial"]["sha256"]
    tls={"currentMaterial":certificate(CERT),"containers":current,"unrelatedContainerIdsPreserved":True,"tlsMaterialPreserved":True,"bff":{}}
    negatives=[];responses={}
    for channel,(port,endpoint) in CHANNELS.items():
        cert=served(port,CERT)
        assert cert["sha256"]==tls["currentMaterial"]["sha256"]
        health=request(port,"/actuator/health");assert health["status"]==200
        tls["bff"][channel]={"certificate":cert,"health":health}
        path=f"/api/{channel}/accounts/{account}/{endpoint}"
        ok=request(port,path,t[channel]);assert ok["status"]==200,ok
        body=ok["response"]
        fields={"web":{"accountId","holderName","accountType","originalBalance","appliedRate","processedBalance","movements","recentAnomalies"},
                "mobile":{"accountId","balance","accountType"},"atm":{"accountId","availableBalance"}}[channel]
        assert set(body)==fields and body["accountId"]==account,body
        responses[channel]=body
        eft.evidence("etapa5-03-"+channel+"-200.json",{"legacyAccountId":account,"request":ok})
        wrong=t["mobile" if channel=="web" else "web"]
        for name,value,expected in [("sin-token",None,401),("rol-otro-canal",wrong,403)]:
            result=request(port,path,value);assert result["status"]==expected,result
            negatives.append({"channel":channel,"case":name,"request":result})
    assert responses["web"]["processedBalance"]==responses["mobile"]["balance"]==responses["atm"]["availableBalance"]
    assert eft.scalar(f"SELECT COUNT(*) FROM eft_account WHERE account_id={account}")=="0"
    eft.evidence("etapa5-02-tls-vigente-https-health.json",tls)
    eft.evidence("etapa5-04-oauth-401-403.json",negatives)
def resilience():
    token=tokens()["mobile"];account=legacy();port=8082
    path=f"/api/mobile/accounts/{account}/summary"
    before=request(port,path,token);assert before["status"]==200,before
    circuitBefore=request(port,"/actuator/circuitbreakers",token)
    initial=inventory()
    try:
        eft.run(["docker","compose","stop","account-service"],timeout=60)
        down=request(port,path,token);assert down["status"]==503 and down["response"]["code"]=="ACCOUNT_SERVICE_UNAVAILABLE",down
        eft.evidence("etapa5-05-mobile-resilience-503.json",{"before":before,"accountStopped":True,"fallback":down,"circuitBefore":circuitBefore,"circuitAfterFailure":request(port,"/actuator/circuitbreakers",token)})
    finally:
        eft.run(["docker","compose","start","account-service"],timeout=60)
    deadline=time.monotonic()+180
    while time.monotonic()<deadline:
        recovered=request(port,path,token)
        if recovered["status"]==200:break
        time.sleep(3)
    assert recovered["status"]==200,recovered
    final=inventory()
    assert all(initial[s]["ID"]==final[s]["ID"] for s in initial), "Contenedores recreados durante resilience"
    eft.evidence("etapa5-06-mobile-recuperacion-200.json",{"sameUrl":before["url"]==recovered["url"],
                  "recovery":recovered,"containers":final,"containerIdsPreserved":True,"circuitRecovered":request(port,"/actuator/circuitbreakers",token)})
if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("phase",choices=["diagnose","regression","resilience"])
    args=parser.parse_args();globals()[args.phase]()
