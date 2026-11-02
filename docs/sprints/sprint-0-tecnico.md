# Sprint 0 técnico

Objetivo: que cualquier integrante pueda clonar el repo, levantar todo con un comando, iniciar sesión con un usuario de prueba y ver una pantalla con el sistema de diseño. Ninguna historia de negocio entra acá.

## Tareas

| # | Tarea | Resultado verificable |
|---|---|---|
| 1 | Estructura del monorepo, `.gitattributes`, `.gitignore`, protección de `main` en GitHub | PR requerido para `main`, con una aprobación |
| 2 | `docker-compose.yml` para desarrollo: PostgreSQL 17 con volumen | `docker compose up -d db` levanta la base |
| 3 | Backend: Spring Boot con Java 25 y Maven wrapper, paquetes por módulo | `./mvnw spring-boot:run` arranca |
| 4 | Flyway con V1 y V2 de `backend/src/main/resources/db/migration` | La base queda con 38 tablas y los catálogos |
| 5 | Entidades JPA de `usuario`, `rol`, `usuario_rol`, `parametro`, `salon`, `turno` | Tests de repositorio con Testcontainers |
| 6 | Spring Security + JWT: acceso corto en memoria, refresco en cookie `HttpOnly`; `POST /api/v1/auth/login`, `/refresh`, `/logout` | Test de integración de login correcto, incorrecto y usuario dado de baja |
| 7 | Usuario inicial de Dirección creado al arrancar en perfil `dev` (contraseña desde variable de entorno) | Se puede iniciar sesión en local |
| 8 | Manejo de errores uniforme (`ProblemDetail`) con mensajes en voseo y palabras del negocio | Un 409 devuelve un mensaje legible |
| 9 | OpenAPI con springdoc en `/api/docs` | Swagger UI accesible en dev |
| 10 | Frontend: Vite + React 18 + TypeScript, React Router, cliente HTTP con renovación de token | `npm run dev` levanta |
| 11 | Portar el sistema de diseño a TypeScript en `frontend/src/ds/` (tokens.css, fuentes, 25 componentes, tipos de `index.d.ts`) | Una página `/_ds` muestra todos los componentes |
| 12 | Agregar al sistema de diseño los estados que faltan: **Contratado, En curso, Cerrado, Liberada** (ícono, chip y relleno en la agenda) | Los 9 estados se ven en `/_ds` |
| 13 | Pantallas UI-01 (login) y UI-02 (sesión vencida) funcionando contra el backend | Login real de punta a punta |
| 14 | Layout base con `Nav` lateral (escritorio) e inferior (teléfono), menú según perfil | Cada perfil ve solo sus ítems |
| 15 | GitHub Actions: build y tests de backend y frontend en cada PR | El PR no se puede fusionar con tests en rojo |
| 16 | PWA mínima: manifest con los íconos de `frontend/public/brand`, HTTPS en el ambiente de prueba | Se instala en el celular |
| 17 | Ambiente de prueba desplegado (ver hosting) con Docker Compose, Caddy como proxy con HTTPS automático | URL pública con candado |

## Orden sugerido

1-2-3-4 primero (una persona). Después en paralelo: backend 5-6-7-8-9, frontend 10-11-12, infraestructura 15-16-17. Cierra con 13-14, que integran todo.

## Hosting

Presupuesto máximo US$ 50 en total, mejor si es cero.

1. **Oracle Cloud Always Free**, región São Paulo (menor latencia desde Córdoba). Hoy da hasta 2 OCPU Arm y 12 GB de RAM gratis; alcanza de sobra. Pide tarjeta para validar la cuenta y a veces no hay capacidad Arm disponible en la región.
2. Si no hay capacidad: un VPS chico pago (del orden de US$ 6–8 por mes); tres meses de piloto quedan dentro del presupuesto.

En ambos casos: Docker Compose (base + backend + frontend estático + Caddy), respaldo diario de la base (`pg_dump`) a otro almacenamiento. HTTPS es obligatorio porque la cámara del celular no funciona sin él. Dominio: un subdominio gratuito alcanza para el piloto.
