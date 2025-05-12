package com.example.tasktracker.backend;

import com.example.tasktracker.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EnableAutoConfiguration(exclude =
        {DataSourceAutoConfiguration.class, LiquibaseAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
class TaskTrackerBackendApplicationTests {

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void contextLoads() {
    }

}
