# Overlay applied on the test workspace during Infra-Stress-Test-Scale SCALE_UP.
environment               = "stress"
instance_type             = "db.m7g.large"
multi_az                  = false
use_fargate_spot          = false
enable_instance_scheduler = false
dedicated_fargate         = true
load_generator_tasks      = 2

data_plane_hostname    = "app.invsys.com"
control_plane_hostname = "admin.invsys.com"
data_plane_api_port    = 8080
control_plane_api_port = 8081
control_plane_cidr_allowlist = []
