#!/bin/bash

REALM_JSON="$DOCKER_FILES_HOMEDIR/keycloak/realms//ironbucket-lab-realm.json"
LOGFILE="/opt/minio/minio-setup.log"

# MinIO configuration
MINIO_ALIAS="dev"
MINIO_URL="http://localhost:9000"
ACCESS_KEY="MINIO_ROOT_USER"
SECRET_KEY="MINIO_ROOT_PASSWORD"
# Setup log
exec > >(tee -a "$LOGFILE") 2>&1

echo "Starting MinIO setup at $(date)"

# Connect MinIO
mc alias set $MINIO_ALIAS $MINIO_URL $ACCESS_KEY $SECRET_KEY

# Extract all unique buckets
buckets=$(jq -r '.groups[].attributes.storage_permissions[] | fromjson.rules[].bucket' "$REALM_JSON" | sort -u)
echo "Creating buckets..."
while IFS= read -r bucket; do
   [ "$bucket" != "*" ] && mc mb --ignore-existing $MINIO_ALIAS/$bucket && echo "Created: $bucket"
done <<< "$buckets"

# Create policies from each group
groups=$(jq -c '.groups[]' "$REALM_JSON")
echo "Creating policies..."
while IFS= read -r row; do
  group=$(echo "$row" | jq -r '.name')
  policy_name="${group}-policy"
  policy_file="policy-${group}.json"

  echo "Generating policy for group $group → $policy_file"
  
  echo '{"Version":"2012-10-17","Statement":[' > "$policy_file"
  
  jq -r '.attributes.storage_permissions[]' <<< "$row" | while read -r perm; do

    echo "$perm" | jq -c '.rules[]' | while read -r rule; do
      bucket=$(echo "$rule" | jq -r '.bucket')
      prefixlist=$(echo "$rule" | jq -c '.prefixes[]')
      while IFS= read -r prefix; do
        path=$(echo "$prefix" | jq -r '.name')
        actions=$(echo "$prefix" | jq -r '[.allowedMethods[] | if . == "UPLOAD" then "s3:PutObject" elif . == "DOWNLOAD" then "s3:GetObject" elif . == "DELETE" then "s3:DeleteObject" elif . == "LIST" then "s3:ListBucket" else empty end] | unique | @csv')

        res="\"arn:aws:s3:::$bucket"
        [[ "$path" != "" && "$path" != "/" ]] && res="$res/$path"
        res="$res*\""

        echo "  {\"Effect\":\"Allow\",\"Action\":[$actions],\"Resource\":[$res]}," >> "$policy_file"
      done <<< "$prefixlist"
    done
  done
  # Clean up trailing comma
  sed -i '$ s/},$/}]/' "$policy_file"
  echo '}' >> "$policy_file"
  cat $policy_file
  mc admin policy create $MINIO_ALIAS "$policy_name" "$policy_file"
done <<< "$groups"

# Create users and assign policies
echo "Creating users..."
jq -c '.users[]' "$REALM_JSON" | while read -r user; do
  username=$(echo "$user" | jq -r '.username')
  password=$(echo "$user" | jq -r '.credentials[0].value')
  group=$(echo "$user" | jq -r '.groups[0]')
  policy="${group}-policy"

  echo "Creating user: $username, group: $group, policy: $policy"
  mc admin user add $MINIO_ALIAS "$username" "$password"
  mc admin group add $MINIO_ALIAS $group-Group $username
  mc admin policy attach $MINIO_ALIAS "$policy" --group "$group-Group"

  echo "$username credentials:"
  mc admin accesskey create $MINIO_ALIAS/ $username

done

echo "Setup complete for realm. Log saved to $LOGFILE"
