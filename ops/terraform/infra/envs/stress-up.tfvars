# Overlay applied on the test workspace during Infra-Stress-Test-Scale SCALE_UP.
environment               = "stress"
instance_type             = "db.m7g.large"
multi_az                  = false
use_fargate_spot          = false
enable_instance_scheduler = false
dedicated_fargate         = true
load_generator_tasks      = 2
