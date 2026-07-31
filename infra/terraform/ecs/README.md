# mapoker ECS Terraform Walkthrough

mapoker を AWS ECS/Fargate に載せる構成を学ぶための Terraform 例です。
デプロイ実行よりも、`terraform validate` / `terraform plan` を見ながらリソース同士の関係を理解することを目的にしています。

## 全体像

```text
Browser
  |
  v
Route 53
  |
  v
Application Load Balancer
  `-- backend ECS/Fargate

Public subnets:
  - Internet-facing ALB

Private subnets:
  - ECS Fargate backend task
  - RDS PostgreSQL
  - VPC endpoints for ECR, CloudWatch Logs, Secrets Manager, S3

Optional:
  - NAT Gateway for external APIs such as Google OAuth
```

ACM certificate は subnet 内に置くリソースではなく、ALB の HTTPS listener が参照するリージョナルな証明書です。
Application Auto Scaling も Fargate task を囲む箱ではなく、ECS service の `desired_count` を調整する制御プレーンです。

## 何を直したか

前の案では frontend nginx を ECS に置いていましたが、ALB があるなら backend の reverse proxy としての nginx は不要です。
React の静的配信は ECS から外し、backend だけ ECS/Fargate にします。

このアプリは frontend が `/api/v1/...` を呼び、backend は `/v1/...` を受けます。
ここは AWS だけで吸収しようとすると CloudFront Function や API Gateway mapping が必要になり、構成が読みづらくなります。
実運用では frontend 側の API prefix を環境変数化するか、backend 側で `/api/v1/...` も受けるようにする方が単純です。

API Gateway は HTTP の prefix rewrite には使えますが、既存の Spring/STOMP WebSocket をそのまま透過 proxy する構成には向きません。
このアプリの WebSocket 入口は ALB の方が自然です。

## Step 1: Network

`network.tf` は以下を作ります。

- VPC
- public subnets: ALB と NAT Gateway 用
- private subnets: ECS task と RDS 用
- Internet Gateway
- VPC endpoints

ECS task は private subnet に置き、`assign_public_ip = false` にしています。

NAT Gateway は `enable_nat_gateway = false` がデフォルトです。
この状態でも ECR pull、CloudWatch Logs、Secrets Manager、S3 は VPC endpoints で使えます。
ただし Google OAuth など外部インターネットへ出る処理は動かないため、必要なら `enable_nat_gateway = true` にします。

## Step 2: Security Groups

`security_groups.tf` の境界は単純です。

- ALB: Internet から HTTP `80`
- ECS task: ALB から backend port `8080`
- RDS: ECS task から PostgreSQL `5432`
- VPC endpoints: ECS task から HTTPS `443`

## Step 3: Route 53 / HTTPS / KMS

`route53.tf` は、既存 hosted zone がある場合だけ ALB alias record を作ります。
`alb_certificate_arn` を渡すと ALB の HTTPS listener を作り、HTTP は HTTPS redirect にします。

`kms.tf` は学習用の customer managed key を作ります。
Secrets Manager、RDS、CloudWatch Logs の暗号化に使います。

## Step 4: ALB

`alb.tf` は backend ECS 用の ALB と target group を作ります。
Fargate なので target group は `target_type = "ip"` です。

ALB は public subnet、ECS task は private subnet です。
health check は Spring Boot の `/actuator/health` を見ます。

## Step 5: ECS

`ecs.tf` は backend だけを定義します。

読む順番:

1. `aws_cloudwatch_log_group.backend`
2. `aws_ecs_cluster.this`
3. `aws_ecs_task_definition.backend`
4. `aws_ecs_service.backend`

Fargate の要点:

- `requires_compatibilities = ["FARGATE"]`
- `network_mode = "awsvpc"`
- `cpu = 512`, `memory = 1024` は有効な組み合わせ
- `assign_public_ip = false`
- `deployment_minimum_healthy_percent = 100`
- `deployment_maximum_percent = 200`
- `deployment_circuit_breaker` で失敗時 rollback
- `health_check_grace_period_seconds = 90`

## Step 6: Auto Scaling

`autoscaling.tf` で backend ECS service の desired count を調整します。

デフォルト:

- `desired_count = 1`
- `autoscaling_min_capacity = 1`
- `autoscaling_max_capacity = 3`
- CPU target tracking: `70%`

ECS Auto Scaling が `desired_count` を変更するため、`aws_ecs_service.backend` では Terraform がそれを戻さないように `lifecycle.ignore_changes = [desired_count]` を入れています。

WebSocket ゲームでは CPU だけが完全な指標ではありません。
接続数やテーブル数でスケールしたい場合は、後で custom metric を CloudWatch に出す設計にします。

## Step 7: Database And Secrets

`database.tf` は RDS PostgreSQL と Secrets Manager を作ります。
DB は private subnet の subnet group に置き、public access は無効です。
この構成では DB subnet group は 2 AZ の private subnet を持ちますが、RDS instance 自体は Single-AZ です。
本番でAZ障害への耐性を上げる場合は、別途 `multi_az = true` を検討します。

注意: Terraform で secret value を生成すると Terraform state にも値が残ります。
本番では既存 secret を参照する形に変える方が安全です。

## Commands

```bash
cd infra/terraform/ecs
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform validate
terraform plan
```

`terraform plan` は AWS 認証が必要です。
作成する場合だけ `terraform apply` を使います。

## Cost Notes

固定費になりやすいのは ALB、RDS、VPC interface endpoints、必要なら NAT Gateway です。
学習だけなら apply せず plan まで、apply するなら短時間で destroy まで確認するのが安全です。
