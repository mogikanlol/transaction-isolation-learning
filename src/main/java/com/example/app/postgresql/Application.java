package com.example.app.postgresql;

import lombok.SneakyThrows;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class Application {


    @SneakyThrows
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Application.class);

        JdbcTemplate jdbcTemplate = ctx.getBean(JdbcTemplate.class);

        TransactionTemplate transactionTemplate = ctx.getBean(TransactionTemplate.class);

        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED);

        prepareDataSet(jdbcTemplate);

        // https://www.postgresql.org/docs/9.5/transaction-iso.html

        // https://en.wikipedia.org/wiki/Isolation_(database_systems)

        // https://dev.to/techschoolguru/understand-isolation-levels-read-phenomena-in-mysql-postgres-c2e#serialization-anomaly-in-postgres

        // https://stackoverflow.com/questions/7705273/what-are-the-conditions-for-encountering-a-serialization-failure

        NonrepeatableRead nonrepeatableRead = new NonrepeatableRead(jdbcTemplate, transactionTemplate);
        nonrepeatableRead.tryReproduce();

        TimeUnit.SECONDS.sleep(5);

        PhantomRead phantomRead = new PhantomRead(jdbcTemplate, transactionTemplate);
        phantomRead.tryReproduce();

        TimeUnit.SECONDS.sleep(5);

        SerializationAnomaly serializationAnomaly = new SerializationAnomaly(jdbcTemplate, transactionTemplate);
        serializationAnomaly.tryReproduce();

    }

    public static void prepareDataSet(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DELETE FROM PERSON");
        jdbcTemplate.execute("DELETE FROM COUNTER");
        jdbcTemplate.execute("INSERT INTO PERSON (first_name, last_name) VALUES ('TARO', 'YOKO')");
        jdbcTemplate.execute("INSERT INTO COUNTER (value) VALUES (1)");
        jdbcTemplate.execute("INSERT INTO COUNTER (value) VALUES (2)");
        jdbcTemplate.execute("INSERT INTO COUNTER (value) VALUES (3)");
    }

}
