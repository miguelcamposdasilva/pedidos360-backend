# Compila el microservicio y lo despliega en la instancia EC2.
#
# Uso:
#   .\despliegue\desplegar.ps1 -Ip 54.123.45.67 -Llave C:\ruta\labsuser.pem
#
# La IP publica del EC2 cambia cada vez que el Learner Lab se reinicia:
# hay que pasarla como parametro en cada despliegue.

param(
    [Parameter(Mandatory = $true)]
    [string] $Ip,

    [Parameter(Mandatory = $true)]
    [string] $Llave,

    [string] $Usuario = "ec2-user"
)

$ErrorActionPreference = "Stop"

$raiz = Split-Path -Parent $PSScriptRoot
$jar  = Join-Path $raiz "target\pedidos-service-1.0.0.jar"

Write-Host "==> Compilando..." -ForegroundColor Cyan

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Users\migue\.jdks\ms-21.0.7"
}

mvn -q -f (Join-Path $raiz "pom.xml") -DskipTests clean package

if (-not (Test-Path $jar)) {
    throw "No se genero el jar en $jar"
}

Write-Host "==> Subiendo el jar a $Ip ..." -ForegroundColor Cyan

# Se sube al home del usuario: /opt pertenece a root y scp entra sin sudo.
scp -o StrictHostKeyChecking=no -i $Llave $jar "${Usuario}@${Ip}:~/pedidos-service.jar"

Write-Host "==> Reiniciando el servicio..." -ForegroundColor Cyan

$comandos = @(
    "sudo mv ~/pedidos-service.jar /opt/pedidos-service/pedidos-service.jar",
    "sudo chown pedidos:pedidos /opt/pedidos-service/pedidos-service.jar",
    "sudo systemctl restart pedidos.service",
    "sleep 12",
    "sudo systemctl is-active pedidos.service",
    "curl -s -o /dev/null -w 'ping local: HTTP %{http_code}\n' http://localhost:8080/api/publico/ping"
) -join " && "

ssh -o StrictHostKeyChecking=no -i $Llave "${Usuario}@${Ip}" $comandos

Write-Host "==> Listo. Comprobar desde aqui:" -ForegroundColor Green
Write-Host "    curl.exe -i http://${Ip}:8080/api/publico/ping"
