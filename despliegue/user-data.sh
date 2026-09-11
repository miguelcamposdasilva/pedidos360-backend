#!/bin/bash
#
# Script de arranque para la instancia EC2 (Amazon Linux 2023).
# Se pega completo en el campo "User data" al lanzar la instancia.
#
# Deja el servidor listo para recibir el jar: instala Java 21, crea el
# usuario del servicio, los directorios y la unidad systemd.
# El jar se sube despues con despliegue/desplegar.ps1
#
set -euxo pipefail

# --- Java 21 -----------------------------------------------------------
dnf update -y
dnf install -y java-21-amazon-corretto-headless

# --- Usuario y directorios del servicio --------------------------------
id pedidos &>/dev/null || useradd --system --shell /sbin/nologin pedidos

mkdir -p /opt/pedidos-service
mkdir -p /var/log/pedidos-service

chown -R pedidos:pedidos /opt/pedidos-service
chown -R pedidos:pedidos /var/log/pedidos-service

# --- Variables de entorno ----------------------------------------------
# Se sobrescriben tras crear RDS. Permisos 600: contiene la contrasena.
if [ ! -f /etc/pedidos-service.env ]; then
  cat > /etc/pedidos-service.env <<'ENV'
DB_URL=jdbc:postgresql://CAMBIAR-ENDPOINT-RDS:5432/pedidos360
DB_USER=postgres
DB_PASSWORD=CAMBIAR
CORS_ORIGINS=
ENV

  chmod 600 /etc/pedidos-service.env
fi

# --- Servicio systemd ---------------------------------------------------
cat > /etc/systemd/system/pedidos.service <<'UNIT'
[Unit]
Description=Pedidos360 - microservicio Spring Boot
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=pedidos
Group=pedidos
EnvironmentFile=/etc/pedidos-service.env
WorkingDirectory=/opt/pedidos-service
ExecStart=/usr/bin/java -jar /opt/pedidos-service/pedidos-service.jar --spring.profiles.active=aws
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable pedidos.service

echo "Servidor preparado. Falta subir el jar y completar /etc/pedidos-service.env"
