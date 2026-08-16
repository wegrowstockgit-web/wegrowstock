@org.springframework.modulith.ApplicationModule(
        displayName = "purchasing",
        allowedDependencies = { "catalog", "inventory :: api", "inventory :: domain", "sales :: domain" }
)
package com.invsys.modules.purchasing;
