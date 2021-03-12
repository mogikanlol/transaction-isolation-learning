package com.example.app.postgresql;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;

@RequiredArgsConstructor
public class NonrepeatableRead {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public void tryReproduce() {
        System.out.println("--> Executing NonrepeatableRead");

        final Object obj = new Object();
        final CountDownLatch latch = new CountDownLatch(1);

        Runnable r1 = () -> transactionTemplate.executeWithoutResult(
                (s) -> {

                    // Select first name
                    String firstName_1 = jdbcTemplate.queryForObject("SELECT first_name from PERSON WHERE last_name = 'YOKO'", String.class);

                    // Resume the second thread
                    latch.countDown();

                    // And wait until it's done
                    synchronized (obj) {
                        try {
                            obj.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    // Select first name one more time
                    String firstName_2 = jdbcTemplate.queryForObject("SELECT first_name from PERSON WHERE last_name = 'YOKO'", String.class);

                    // if it differs, anomaly occurs
                    if (!firstName_1.equals(firstName_2)) {
                        System.err.println("NonrepeatableRead was occurred");
                    } else {
                        System.out.println("NonrepeatableRead was prevented");
                    }
                }
        );

        Runnable r2 = () -> {

            // Wait for the first thread to select first name
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Change first name
            transactionTemplate.executeWithoutResult((s) -> {
                jdbcTemplate.execute("UPDATE person SET first_name = 'name was changed in another transaction' WHERE last_name = 'YOKO'");
            });

            // Resume the first thread
            synchronized (obj) {
                obj.notify();
            }
        };

        Thread t1 = new Thread(r1);
        Thread t2 = new Thread(r2);

        t1.start();
        t2.start();
    }
}
