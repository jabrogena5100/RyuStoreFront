#!/usr/bin/env bash
# After destroying the session stage: check that nothing billable is left. Direct service calls are the
# source of truth; the tag search at the end lags behind deletions (it can still list a resource for a while),
# so a hit there is a prompt to look, not proof.
#
#   export AWS_PROFILE=ryustorefront
#   scripts/aws-audit.sh
set -uo pipefail

REGION="${AWS_REGION:-us-west-2}"
export AWS_DEFAULT_REGION="$REGION"
FAIL=0

check() { # check <label> <command output that must be empty>
  local label="$1" out="$2"
  if [[ -z "$out" || "$out" == "None" ]]; then
    echo "ok       $label"
  else
    echo "LEFTOVER $label: $out"
    FAIL=1
  fi
}

ZONE_ID="$(aws route53 list-hosted-zones-by-name --dns-name ryustorefront.net --query 'HostedZones[0].Id' --output text | sed 's|/hostedzone/||')"

check "RDS instances"          "$(aws rds describe-db-instances --query 'DBInstances[].DBInstanceIdentifier' --output text)"
check "load balancers"         "$(aws elbv2 describe-load-balancers --query 'LoadBalancers[].LoadBalancerName' --output text)"
check "target groups"          "$(aws elbv2 describe-target-groups --query 'TargetGroups[].TargetGroupName' --output text)"
check "ECS clusters"           "$(aws ecs list-clusters --query 'clusterArns' --output text)"
check "VPCs tagged storefront" "$(aws ec2 describe-vpcs --filters Name=tag:Project,Values=storefront --query 'Vpcs[].VpcId' --output text)"
check "NAT gateways"           "$(aws ec2 describe-nat-gateways --filter Name=state,Values=pending,available --query 'NatGateways[].NatGatewayId' --output text)"
check "unattached Elastic IPs" "$(aws ec2 describe-addresses --query 'Addresses[?AssociationId==null].PublicIp' --output text)"
check "ACTIVE ECS task definitions" "$(aws ecs list-task-definitions --family-prefix storefront --status ACTIVE --query 'taskDefinitionArns' --output text)"
check "security groups storefront-*" "$(aws ec2 describe-security-groups --filters Name=group-name,Values='storefront-*' --query 'SecurityGroups[].GroupId' --output text)"
check "log group /storefront"  "$(aws logs describe-log-groups --log-group-name-prefix /storefront --query 'logGroups[].logGroupName' --output text)"
check "IAM roles storefront-*" "$(aws iam list-roles --query "Roles[?starts_with(RoleName, 'storefront')].RoleName" --output text)"
check "DNS record api.ryustorefront.net" "$(aws route53 list-resource-record-sets --hosted-zone-id "$ZONE_ID" --query "ResourceRecordSets[?Name=='api.ryustorefront.net.' && Type=='A'].Name" --output text)"
check "RDS-managed secrets (rds!...)" "$(aws secretsmanager list-secrets --query "SecretList[?starts_with(Name, 'rds!')].Name" --output text)"

echo
echo "Expected to remain (foundation, kept on purpose):"
echo "  zone:    $ZONE_ID"
echo "  ECR:     $(aws ecr describe-repositories --query 'repositories[].repositoryName' --output text)"
echo "  secrets: $(aws secretsmanager list-secrets --query "SecretList[?starts_with(Name, 'storefront/')].Name" --output text)"
echo "  certs:   $(aws acm list-certificates --query 'CertificateSummaryList[].DomainName' --output text)"
echo
echo "Tag search (lags after deletions; trust the direct checks above):"
aws resourcegroupstaggingapi get-resources --tag-filters Key=Project,Values=storefront --query 'ResourceTagMappingList[].ResourceARN' --output text | tr '\t' '\n' | sed 's/^/  /'

exit "$FAIL"
