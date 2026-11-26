#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 3 || ! $2 =~ ^[a-z0-9-]+$ || ! $3 =~ ^i-([0-9a-f]{8}|[0-9a-f]{17})$ ]]; then
  echo "Usage: bash $0 {start|stop|status} REGION INSTANCE_ID" >&2
  exit 2
fi

action=$1
region=$2
instance=$3
ec2() { aws --no-cli-pager --region "$region" ec2 "$@"; }

case "$action" in
  start)
    ec2 start-instances --instance-ids "$instance" >/dev/null
    ec2 wait instance-status-ok --instance-ids "$instance"
    ;;
  stop)
    ec2 stop-instances --instance-ids "$instance" >/dev/null
    ec2 wait instance-stopped --instance-ids "$instance"
    ;;
  status) ;;
  *) echo "Unknown action: $action" >&2; exit 2 ;;
esac

ec2 describe-instances --instance-ids "$instance" --output table \
  --query 'Reservations[].Instances[].{Instance:InstanceId,State:State.Name,PublicIPv4:PublicIpAddress}'
