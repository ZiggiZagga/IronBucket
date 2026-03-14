ui = true
disable_mlock = true

api_addr = "https://steel-hammer-vault:8200"
cluster_addr = "https://steel-hammer-vault:8201"

listener "tcp" {
  address = "0.0.0.0:8200"
  tls_cert_file = "/certs/services/infrastructure/vault/tls.crt"
  tls_key_file = "/certs/services/infrastructure/vault/tls.key"
}

storage "file" {
  path = "/vault/file"
}
