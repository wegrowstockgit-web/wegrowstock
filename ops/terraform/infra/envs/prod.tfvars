environment               = "prod"
instance_type             = "db.m7g.large"
multi_az                  = true
use_fargate_spot          = false
enable_instance_scheduler = false
dedicated_fargate         = true
load_generator_tasks      = 0

# Plane routing — tighten control-plane CIDRs before go-live
data_plane_hostname    = "app.invsys.com"
control_plane_hostname = "admin.invsys.com"
data_plane_api_port    = 8080
control_plane_api_port = 8081
# Example VPN / office ranges — replace with real CIDRs; empty = allow-all (not recommended for prod).
control_plane_cidr_allowlist = [
  # "10.0.0.0/8",
  # "192.168.0.0/16",
]
