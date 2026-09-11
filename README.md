# pedidos-service

Microservicio REST en Spring Boot que gestiona pedidos y protege sus endpoints
con **OAuth 2.0 / OpenID Connect** contra **Microsoft Entra ID**.

Es el backend del proyecto **Pedidos360**, de la asignatura Desarrollo Cloud
Native I. La arquitectura completa, **desplegada y verificada en AWS**:

```
Angular + MSAL (Authorization Code + PKCE)
        ↓  Bearer JWT
AWS API Gateway  (valida firma, issuer, audience, vigencia · CORS)
        ↓
Spring Boot en EC2  (revalida el JWT · autoriza por roles)
        ↓
PostgreSQL en AWS RDS
```

El JWT se valida **en las dos capas**. El gateway es la primera línea, no la
única: si alguien llamara directamente al EC2 saltándose el gateway, recibiría
igualmente `401`. Eso es defensa en profundidad.

---

## Stack

| Componente | Versión |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.6 |
| Spring Security | OAuth2 Resource Server |
| Spring Data JPA | Hibernate 6 |
| PostgreSQL | 16 |
| Maven | 3.9+ |

## Estructura

```
src/main/java/com/pedidos360/pedidos/
├── PedidosServiceApplication.java
├── config/
│   ├── SecurityConfig.java        cadena de filtros, decoder JWT, roles, CORS
│   └── AudienceValidator.java     validación del claim "aud"
├── model/
│   ├── Pedido.java                entidad JPA
│   └── EstadoPedido.java
├── repository/
│   └── PedidoRepository.java      JpaRepository
├── service/
│   └── PedidoService.java         reglas de negocio y transacciones
├── controller/
│   ├── PedidoController.java      endpoints de pedidos
│   └── IdentidadController.java   /api/me y endpoint público
├── dto/
│   └── PedidoRequest.java         entrada validada con Bean Validation
└── exception/
    └── ManejadorDeErrores.java    respuestas JSON uniformes ante 400
```

El controlador solo traduce HTTP y delega en el servicio; el acceso a datos
vive en el repositorio.

---

## Ejecutar en local

**Requisitos:** JDK 21, Maven 3.9+, Docker.

```bash
docker compose up -d      # PostgreSQL 16 en localhost:5432
mvn spring-boot:run       # API en http://localhost:8080
```

Hibernate crea la tabla `pedidos` automáticamente (`ddl-auto: update`).

### Perfiles

| Perfil | Base de datos | CORS | Cómo se activa |
|---|---|---|---|
| *(ninguno)* | Docker local | `http://localhost:4200` | por defecto |
| `local` | AWS RDS | `http://localhost:4200` | `mvn spring-boot:run -Dspring-boot.run.profiles=local` |
| `aws` | AWS RDS | lo maneja API Gateway | `java -jar app.jar --spring.profiles.active=aws` |

El perfil `local` se define en **`config/application-local.yml`**, un archivo
fuera del jar y excluido del repositorio, porque contiene la contraseña real de
RDS:

```yaml
# config/application-local.yml
spring:
  datasource:
    url: jdbc:postgresql://<endpoint>.us-east-1.rds.amazonaws.com:5432/pedidos360
    username: postgres
    password: <tu-password>

cors:
  allowed-origins: http://localhost:4200
```

En el perfil `aws` la configuración llega por variables de entorno
(`DB_URL`, `DB_USER`, `DB_PASSWORD`, `CORS_ORIGINS`), leídas desde
`/etc/pedidos-service.env` en el servidor. Ver [`.env.example`](.env.example).

> El mismo jar corre en local y en AWS: solo cambia la configuración externa.
> Es el principio de configuración de las
> [Twelve-Factor Apps](https://12factor.net/config).

---

## Endpoints

| Método | Ruta | Autorización | Respuesta |
|---|---|---|---|
| `GET` | `/api/publico/ping` | pública | `200` sin token |
| `GET` | `/api/me` | token válido | claims que lee el backend |
| `GET` | `/api/pedidos` | `Pedidos.Lector` o `Pedidos.Admin` | `200` |
| `GET` | `/api/pedidos/{id}` | `Pedidos.Lector` o `Pedidos.Admin` | `200` / `404` |
| `POST` | `/api/pedidos` | `Pedidos.Admin` | `201` / `403` |
| `DELETE` | `/api/pedidos/{id}` | `Pedidos.Admin` | `204` / `403` |
| `GET` | `/actuator/health` | pública | estado del servicio |

Comportamiento ante peticiones inválidas:

| Situación | Código |
|---|---|
| Sin token, token malformado, expirado, firma inválida o audiencia ajena | `401 Unauthorized` |
| Token válido pero sin el rol requerido | `403 Forbidden` |
| Cuerpo que no pasa las validaciones | `400 Bad Request` con el detalle por campo |

`/api/me` es útil en la demostración: devuelve `iss`, `aud`, `tid`, `roles`,
`scp` y las autoridades de Spring, es decir, lo que el microservicio está
leyendo realmente del token.

El campo `creadoPor` de un pedido **no se toma del cuerpo de la petición** sino
del claim `preferred_username`, de modo que un usuario no puede registrar
pedidos a nombre de otro.

---

## Validación del token

`SecurityConfig` construye un `NimbusJwtDecoder` a partir del issuer de Entra
ID, que descarga las claves públicas del JWKS publicado en
`/.well-known/openid-configuration`. Se comprueba:

| Aspecto | Quién lo valida |
|---|---|
| **Firma** | `NimbusJwtDecoder` contra el JWKS de Entra |
| **Issuer** (`iss`) | `JwtValidators.createDefaultWithIssuer` |
| **Vigencia** (`exp`, `nbf`) | validador por defecto |
| **Audience** (`aud`) | `AudienceValidator` — *Spring no lo valida por defecto* |

Sin el validador de audiencia, un token emitido por el mismo tenant para otra
API sería aceptado. Por eso es una clase propia y no una omisión.

### Roles y scopes

Un `JwtAuthenticationConverter` traduce los claims de Entra a autoridades:

```
claim "roles"  →  ROLE_Pedidos.Admin, ROLE_Pedidos.Lector
claim "scp"    →  SCOPE_access_as_user
```

Las reglas se declaran de forma centralizada en `SecurityConfig`, no con
anotaciones dispersas por los controladores.

### CORS

La lista de orígenes se lee de `cors.allowed-origins`. Si está **vacía**, el
filtro no registra ningún mapeo y Spring no añade cabeceras CORS: es el modo
correcto detrás de API Gateway, que ya las agrega. Si ambos las añadieran, el
navegador recibiría dos valores en `Access-Control-Allow-Origin` y bloquearía
la respuesta.

---

## Configuración en Microsoft Entra ID

**App registration de la API** (`323d4ae1-4cd7-4f3b-a10d-6cecd530b09d`):

1. *Expose an API* → Application ID URI `api://<client-id>` y scope
   `access_as_user`.
2. *App roles* → `Pedidos.Admin` y `Pedidos.Lector`, con *Allowed member
   types: Users/Groups*. El campo **Value** es el texto que viaja en el claim
   `roles` y debe coincidir exactamente con `SecurityConfig`.
3. *Manifest* → `"requestedAccessTokenVersion": 2`. Si queda en `null`, Entra
   emite tokens v1 con issuer `sts.windows.net` y la validación falla.
4. *Enterprise applications* → *Users and groups*: asignar cada rol a un
   usuario distinto. Con **Assignment required = No**, un usuario sin rol
   obtiene token pero recibe `403`, que es justamente el caso a demostrar.

**App registration del frontend** (`470a6457-ce66-4860-91f5-ee4e6618e57c`):

5. *Authentication* → plataforma **SPA** con `http://localhost:4200`.
6. *API permissions* → permiso delegado `access_as_user` de la API.

---

## Despliegue en AWS

### Infraestructura

| Servicio | Configuración |
|---|---|
| **RDS** | PostgreSQL 16, `db.t3.micro`. Puerto 5432 abierto solo al *security group* del EC2 |
| **EC2** | Amazon Linux 2023, `t3.micro`, Java 21. Puertos 22 (SSH) y 8080 |
| **API Gateway** | HTTP API con authorizer JWT, CORS y ruteo hacia el EC2 |

La carpeta [`despliegue/`](despliegue/) automatiza el servidor:

| Archivo | Función |
|---|---|
| `user-data.sh` | se pega en *User data* al lanzar el EC2: instala Java 21, crea el usuario del servicio y la unidad systemd con reinicio automático |
| `desplegar.ps1` | compila, sube el jar por `scp` y reinicia el servicio |

```powershell
.\despliegue\desplegar.ps1 -Ip <ip-ec2> -Llave C:\ruta\labsuser.pem
```

### Configuración del API Gateway

**Authorizer JWT** (`entra-id`):

| Campo | Valor |
|---|---|
| Identity source | `$request.header.Authorization` |
| Issuer URL | `https://login.microsoftonline.com/<tenant>/v2.0` |
| Audience | Client ID de la API |

El sufijo `/v2.0` no es opcional: sin él el gateway no encuentra el documento
de descubrimiento y rechaza **todos** los tokens, incluidos los válidos.

**Rutas**:

| Método | Ruta | Authorizer | Integración |
|---|---|---|---|
| `GET` | `/{proxy+}` | `entra-id` | `http://<ip-ec2>:8080/{proxy}` |
| `POST` | `/{proxy+}` | `entra-id` | `http://<ip-ec2>:8080/{proxy}` |
| `DELETE` | `/{proxy+}` | `entra-id` | `http://<ip-ec2>:8080/{proxy}` |
| `GET` | `/api/publico/ping` | ninguno | `http://<ip-ec2>:8080/api/publico/ping` |

> **Por qué métodos explícitos y no `ANY`.** `ANY` incluye `OPTIONS`, y el
> preflight de CORS viaja **sin token** por especificación: el authorizer lo
> rechazaría con `401` y el navegador cancelaría la petición real. Al declarar
> solo los tres verbos que la API usa, el gateway responde el preflight por su
> cuenta y, de paso, cualquier otro método (`PUT`, `PATCH`…) recibe `404` sin
> llegar al servidor.

**CORS**: origen `http://localhost:4200`, métodos `GET, POST, DELETE, OPTIONS`,
cabeceras `authorization, content-type`.

### Resultados verificados

| Prueba | Respuesta | Quién responde |
|---|---|---|
| `GET /api/publico/ping` | `200` | el microservicio |
| `OPTIONS /api/pedidos` (preflight) | `204` + cabeceras CORS | API Gateway |
| `GET /api/pedidos` sin token | `401` | API Gateway |
| `GET /api/pedidos` token inválido | `401` | API Gateway |
| `GET /api/pedidos` token con rol | `200` | el microservicio |
| `POST /api/pedidos` con `Pedidos.Lector` | `403` | Spring Security |
| `POST /api/pedidos` con `Pedidos.Admin` | `201` + fila en RDS | el microservicio |
| `PUT /api/pedidos` | `404` | API Gateway |

El `401` del gateway se distingue del de Spring por las cabeceras: el del
gateway devuelve `{"message":"Unauthorized"}` con `content-type:
application/json`; el de Spring añade `www-authenticate`, `x-frame-options` y
`x-content-type-options`.

### Diagnóstico

```bash
sudo systemctl status pedidos.service
sudo journalctl -u pedidos.service -n 50 --no-pager
```

> El entorno usado es AWS Academy Learner Lab: al reiniciar la sesión la IP
> pública del EC2 cambia y hay que actualizar las integraciones del API
> Gateway. La regla de entrada de RDS referencia el *security group* del EC2,
> no su IP, precisamente para que sobreviva a ese reinicio.

---

## Pruebas manuales

```bash
# 401 — sin token
curl -i http://localhost:8080/api/pedidos

# 200 — endpoint público
curl -i http://localhost:8080/api/publico/ping

# 200 / 403 — con un token obtenido desde el frontend
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/me
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/pedidos

curl -i -X POST http://localhost:8080/api/pedidos \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"cliente":"Ferretería Sur","descripcion":"20 sacos de cemento","monto":150000}'
```

El token se obtiene desde la pantalla **Detalles técnicos** del frontend, o
configurando OAuth 2.0 en Postman con *Authorization Code (With PKCE)*.

Consultar la base de datos:

```bash
# local
docker exec -it pedidos360-postgres psql -U pedidos -d pedidos360

# RDS, desde el EC2 (requiere: sudo dnf install -y postgresql15)
psql -h <endpoint>.us-east-1.rds.amazonaws.com -U postgres -d pedidos360
```

---

## Seguridad del repositorio

### Qué es secreto y qué no

Los identificadores de Entra ID (`tenantId`, `clientId`, App ID URI) **no son
secretos**: viajan en la URL del navegador durante el login. Un SPA es un
cliente público y, por definición, no puede custodiar secretos — de ahí que se
use PKCE en lugar de un `client_secret`.

Sí son secretos, y nunca deben entrar al repositorio:

| Secreto | Dónde vive |
|---|---|
| Contraseña de RDS | `config/application-local.yml` y `/etc/pedidos-service.env` en el EC2 (permisos 600) |
| `labsuser.pem` | fuera del repositorio, por ejemplo en `~/.ssh/` |
| Credenciales del Learner Lab | solo en `~/.aws/credentials` |
| Access tokens JWT | son efímeros; no se guardan en archivos |

La única credencial literal en el código es `pedidos/pedidos`, del PostgreSQL
**local en Docker**: ese contenedor no es accesible desde fuera de la máquina y
se destruye con `docker compose down -v`. Las credenciales de RDS jamás
aparecen en el código.

### Antes de un push

```bash
git status
git ls-files | grep -Ei "\.env$|\.pem$|credentials|application-local"   # debe salir vacío
```

Activa en GitHub → *Settings → Code security*: **Secret scanning** y
**Push protection**.

### Si un secreto ya llegó al repositorio

1. **Rotar el secreto primero.** Un secreto publicado se considera
   comprometido para siempre.
2. Después limpiar el historial con `git filter-repo` o BFG Repo-Cleaner.

El orden importa: limpiar el historial sin rotar deja la credencial válida en
manos de quien ya la copió.

---

## Estado

| Requisito | Estado |
|---|---|
| Microservicio en Java con Spring Boot | ✅ |
| Base de datos cloud con entidades y repositorios | ✅ RDS PostgreSQL |
| Validación de firma, vigencia, issuer y audience | ✅ |
| Autorización por roles | ✅ App Roles de Entra ID |
| Respuesta correcta ante solicitudes válidas e inválidas | ✅ 200 / 401 / 403 / 404 |
| API Gateway activo con ruteo a EC2 | ✅ |
| CORS configurado | ✅ en el gateway |
| Protección de rutas con validación JWT | ✅ authorizer nativo |

**Pendiente:** el SPA se ejecuta en el entorno de desarrollo y consume la
infraestructura cloud real. Publicarlo requeriría HTTPS (S3 + CloudFront),
ya que Entra ID solo acepta `http` para `localhost`.

**Mejoras para producción:** migraciones versionadas con Flyway en lugar de
`ddl-auto: update`, y el EC2 en subred privada tras un VPC Link para cerrar el
puerto 8080.
