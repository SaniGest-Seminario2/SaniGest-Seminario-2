#!/usr/bin/env bash
set -euo pipefail

: "${AWS_ECS_CLUSTER:?}" "${COMMIT:?}" "${SERVICE_CONFIG:?}" "${AWS_REGION:?}"

account=$(aws sts get-caller-identity --query Account --output text)
registry="$account.dkr.ecr.$AWS_REGION.amazonaws.com"
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
docker_config="$work_dir/docker"
export DOCKER_CONFIG="$docker_config"
mkdir "$docker_config"

stable_on() {
  local revision=$1
  aws ecs wait services-stable --cluster "$AWS_ECS_CLUSTER" --services "$service" &&
    aws ecs describe-services --cluster "$AWS_ECS_CLUSTER" --services "$service" |
    jq -e --arg revision "$revision" '
      .services[0] |
      .taskDefinition == $revision and .desiredCount > 0 and
      .runningCount == .desiredCount and (.deployments | length == 1)
    ' >/dev/null
}

update_service() {
  aws ecs update-service --cluster "$AWS_ECS_CLUSTER" --service "$service" \
    --task-definition "$1" >/dev/null
}

smoke() {
  curl --fail --silent --show-error --max-time 10 --retry 3 --retry-max-time 45 "$health_url" |
    jq -e '.status == "UP"' >/dev/null
}

service=$(jq -r .service <<< "$SERVICE_CONFIG")
repository=$(jq -r .repository <<< "$SERVICE_CONFIG")
health_url=$(jq -r .healthUrl <<< "$SERVICE_CONFIG")
container=$(jq -r '.container // .service' <<< "$SERVICE_CONFIG")
test -f "services/$service/pom.xml"

previous=$(aws ecs describe-services --cluster "$AWS_ECS_CLUSTER" --services "$service" |
  jq -er '.services[0] | select(.status == "ACTIVE" and .desiredCount > 0 and
    .deploymentController.type == "ECS") | .taskDefinition')
aws ecs describe-task-definition --task-definition "$previous" \
  --query taskDefinition --output json > "$work_dir/task.json"
old_image=$(jq -er --arg name "$container" '
  [.containerDefinitions[] | select(.name == $name)] |
  select(length == 1) | .[0].image
' "$work_dir/task.json")
image_path="$registry/$repository"
baseline=${old_image#"$image_path:"}
if [[ "$baseline" =~ ^[0-9a-f]{40}$ ]] && git cat-file -e "$baseline^{commit}" 2>/dev/null; then
  # An earlier run may still be queued when a newer commit has already deployed.
  if [[ "$baseline" != "$COMMIT" ]] && git merge-base --is-ancestor "$COMMIT" "$baseline"; then
    echo "$service already runs a newer revision ($baseline)."
    exit 0
  fi
  if git diff --quiet "$baseline" "$COMMIT" -- "services/$service" libraries config \
    ':(glob)services/*/pom.xml' pom.xml .dockerignore infrastructure/docker scripts/ci/maven-build.sh; then
    echo "$service has no image changes since $baseline."
    exit 0
  fi
fi

image="$image_path:$COMMIT"
# Reuse a previously published immutable image after a retry.
ecr_result=$(aws ecr batch-get-image --repository-name "$repository" --image-ids imageTag="$COMMIT")
if jq -e '.images | length == 1' <<< "$ecr_result" >/dev/null; then
  echo "Reusing $image"
else
  jq -e '.failures | length == 1 and .[0].failureCode == "ImageNotFound"' <<< "$ecr_result" >/dev/null
  aws ecr get-login-password | docker login --username AWS --password-stdin "$registry"
  docker build --file infrastructure/docker/service.Dockerfile --build-arg "SERVICE=$service" \
    --label "org.opencontainers.image.revision=$COMMIT" --tag "$image" .
  docker push "$image"
fi

jq --arg name "$container" --arg image "$image" '
  del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .compatibilities,
      .registeredAt, .registeredBy, .deregisteredAt) |
  (.containerDefinitions[] | select(.name == $name) | .image) = $image
' "$work_dir/task.json" > "$work_dir/new-task.json"

# Confirm the previously captured revision is stable before replacing it.
stable_on "$previous"
revision=$(aws ecs register-task-definition --cli-input-json "file://$work_dir/new-task.json" \
  --query taskDefinition.taskDefinitionArn --output text)
echo "$service: $previous -> $revision ($COMMIT)"

if update_service "$revision" && stable_on "$revision" && smoke; then
  echo "$service deployed and healthy."
else
  echo "::error::$service failed to deploy. Restoring $previous."
  if update_service "$previous" && stable_on "$previous"; then
    echo "$service restored to $previous."
  else
    echo "::error::Rollback of $service could not be verified. Restore $previous manually."
  fi
  exit 1
fi
