## Keycloak TLS Certificates

This directory should contain the TLS certificate and key for Keycloak HTTPS support.

- `tls.crt`: The public certificate (PEM format)
- `tls.key`: The private key (PEM format)

For development, you can generate a self-signed certificate with:

```bash
openssl req -x509 -newkey rsa:4096 -keyout tls.key -out tls.crt -days 365 -nodes -subj "/CN=localhost"
```

For production, use a certificate from a trusted CA.