# InventorySystem Terraform (multi-environment)

GitOps root for Test (resting + burst) and Prod (Multi-AZ) profiles.

## Backend

| Resource | Name |
| --- | --- |
| S3 state bucket | `invsys-terraform-state-storage` |
| DynamoDB lock table | `invsys-infra-lock-table` (`LockID` String) |
| State key | `invsys/infra/terraform.tfstate` |
| Workspaces | `test`, `prod` |

## Profiles (`envs/`)

| File | Intent |
| --- | --- |
| `test.tfvars` | `db.t4g.micro`, Fargate Spot, instance scheduler |
| `prod.tfvars` | `db.m7g.large`, Multi-AZ, dedicated Fargate |
| `stress-up.tfvars` | 2 load generators + `db.m7g.large` on test workspace |
| `stress-down.tfvars` | Return to resting baseline |

## GitHub Actions

| Workflow | Trigger | Action |
| --- | --- | --- |
| `terraform-plan.yml` | PR → `develop` / `main` | fmt, validate, tfsec, plan + PR comment |
| `terraform-apply-test.yml` | Push → `develop` | Apply test profile |
| `terraform-apply-prod.yml` | **DISABLED** | Prod apply job `if: false` |
| `infra-stress-test-scale.yml` | `workflow_dispatch` | Scale up / resting |

### Required GitHub configuration

**Secrets**

- `AWS_ROLE_ARN_TEST` — IAM role ARN trusted by GitHub OIDC for Test
- `AWS_ROLE_ARN_PROD` — IAM role ARN for Prod (unused while apply is disabled)

**Variables**

- `AWS_REGION` (optional, default `us-east-1`)
- `ENABLE_PROD_TF_APPLY` — leave unset/`false` until cutover

**Environments**

- `test` — used by Test apply + stress widget
- `production` — required reviewers (for when prod apply is re-enabled)

## Local commands

```bash
cd ops/terraform/infra
terraform init
terraform workspace select test || terraform workspace new test
terraform plan -var-file=envs/test.tfvars
```

Production apply from CI is disabled. Do not run `terraform apply -var-file=envs/prod.tfvars` against shared state without an explicit change-control approval.
