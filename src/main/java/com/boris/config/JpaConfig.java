package com.boris.config;

import com.boris.memory.ConversationMessage;
import com.boris.memory.ConversationRepository;
import com.boris.memory.MemoryService;
import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Properties;

@Configuration
@EnableJpaRepositories(basePackageClasses = ConversationRepository.class)
@EnableTransactionManagement
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class JpaConfig {

    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("schema.sql")
                .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.boris.memory");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "false");
        em.setJpaProperties(properties);

        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    public ConversationRepository conversationRepository() {
        return null;
    }

    @Bean
    public Settings settings() throws IOException {
        SettingsManager mgr = new SettingsManager();
        String settingsPath = System.getProperty("user.home") + "/.boris/settings.json";
        mgr.ensureExists(settingsPath);
        return mgr.loadSettings(settingsPath);
    }

    @Bean
    public MemoryService memoryService(ConversationRepository repository, Settings settings) {
        Settings.MemoryConfig memoryConfig = settings.getMemory();
        boolean enabled = memoryConfig != null && memoryConfig.getEnabled() != null && memoryConfig.getEnabled();
        int maxContextTokens = (memoryConfig != null && memoryConfig.getMaxContextTokens() != null) ? memoryConfig.getMaxContextTokens() : 8000;
        int maxHistoryMessages = (memoryConfig != null && memoryConfig.getMaxHistoryMessages() != null) ? memoryConfig.getMaxHistoryMessages() : 50;
        String sessionId = (memoryConfig != null && memoryConfig.getSessionId() != null) ? memoryConfig.getSessionId() : "default";

        return new MemoryService(repository, sessionId, maxContextTokens, maxHistoryMessages);
    }
}