# Return test workspace to resting-state baseline cost profile.
environment               = "test"
instance_type             = "db.t4g.micro"
multi_az                  = false
use_fargate_spot          = true
enable_instance_scheduler = true
dedicated_fargate         = false
load_generator_tasks      = 0

data_plane_hostname    = "app.invsys.com"
control_plane_hostname = "admin.invsys.com"
data_plane_api_port    = 8080
control_plane_api_port = 8081
control_plane_cidr_allowlist = []
