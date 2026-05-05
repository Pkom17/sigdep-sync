package ci.itechciv.sigdep.sync;

import ci.itechciv.sigdep.sync.config.SyncProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(SyncProperties.class)
public class SigdepSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(SigdepSyncApplication.class, args);
    }
}
