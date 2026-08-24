@org.springframework.modulith.ApplicationModule(
        displayName = "sales",
        allowedDependencies = { "catalog", "inventory :: api", "inventory :: domain" }
)
package com.invsys.modules.sales;
