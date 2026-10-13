@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "common",
                "common::exception",
                "common::web"
        }
)
package com.acme.sportplatform.identity;