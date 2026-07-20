# Return test workspace to resting-state baseline cost profile.
environment               = "test"
instance_type             = "db.t4g.micro"
multi_az                  = false
use_fargate_spot          = true
enable_instance_scheduler = true
dedicated_fargate         = false
load_generator_tasks      = 0
