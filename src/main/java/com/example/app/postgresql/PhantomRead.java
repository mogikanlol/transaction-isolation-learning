package com.example.app.postgresql;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;

@RequiredArgsConstructor
public class PhantomRead {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public void tryReproduce() {
        System.out.println("--> Executing PhantomRead");

        final Object obj = new Object();
        final CountDownLatch latch = new CountDownLatch(1);

        Runnable r1 = () -> transactionTemplate.executeWithoutResult(
                (s) -> {
                    // Count records
                    Integer count_1 = jdbcTemplate.queryForObject("SELECT count(*) FROM PERSON", Integer.class);

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

                    // Count records one more time
                    Integer count_2 = jdbcTemplate.queryForObject("SELECT count(*) FROM PERSON", Integer.class);

                    // If it differs from initial count, anomaly occurs
                    if (!count_1.equals(count_2)) {
                        System.err.println("PhantomRead was occurred");
                    } else {
                        System.out.println("PhantomRead was prevented");
                    }
                }
        );

        Runnable r2 = () -> {

            // Wait for the first thread to count initials rows
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Insert new row
            transactionTemplate.executeWithoutResult((s) -> {
                jdbcTemplate.execute("INSERT INTO PERSON (first_name, last_name) VALUES ('the phantom', 'pain')");
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
