package cn.innersight.auth.bootstrap;

import cn.innersight.auth.service.IdentityService;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("initialize")
class Bootstrap implements ApplicationRunner {
    private final IdentityService identity;
    private final ConfigurableApplicationContext context;

    Bootstrap(IdentityService identity, ConfigurableApplicationContext context) {
        this.identity = identity;
        this.context = context;
    }

    @Override public void run(ApplicationArguments arguments) {
        if (!"none".equals(context.getEnvironment().getProperty("spring.main.web-application-type")))
            throw new IllegalStateException("初始化必须在非 Web 模式运行。");
        boolean created = identity.initialize(System.getenv("INNERSIGHT_ADMIN_LOGIN"),
                System.getenv("INNERSIGHT_ADMIN_NAME"), System.getenv("INNERSIGHT_ADMIN_PASSWORD"));
        System.out.println(created ? "首个管理员已初始化。" : "数据库已初始化，未更改现有凭据。");
        SpringApplication.exit(context);
    }
}
