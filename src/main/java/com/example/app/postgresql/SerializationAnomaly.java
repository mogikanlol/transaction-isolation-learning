package com.example.app.postgresql;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;

@RequiredArgsConstructor
public class SerializationAnomaly {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public void tryReproduce() {
        System.out.println("--> Executing SerializationAnomaly");

        final Object obj = new Object();
        final CountDownLatch latch = new CountDownLatch(1);

        Runnable r1 = () -> {
            try {
                transactionTemplate.executeWithoutResult(
                        (s) -> {

                            // Summarize values
                            Integer sum = jdbcTemplate.queryForObject("SELECT sum(value) FROM COUNTER", Integer.class);

                            // Insert sum as a new record
                            jdbcTemplate.execute("INSERT INTO COUNTER VALUES (" + sum + ")");

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
                        }
                );
            } catch (Exception ex) {
                checkSerializationError(ex);
            }
        };

        Runnable r2 = () -> {
            try {
                transactionTemplate.executeWithoutResult((s) -> {

                    // Wait for the first thread to summarize and insert
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // Do everything the first thread do
                    // Summarize values
                    Integer sum = jdbcTemplate.queryForObject("SELECT sum(value) FROM COUNTER", Integer.class);

                    // Insert sum as a new record
                    jdbcTemplate.execute("INSERT INTO COUNTER VALUES (" + sum + ")");

                    // Resume the first thread
                    synchronized (obj) {
                        obj.notify();
                    }
                });
            } catch (Exception ex) {
                checkSerializationError(ex);
            }
        };

        Thread t1 = new Thread(r1);
        Thread t2 = new Thread(r2);

        t1.start();
        t2.start();

        // Wait for the threads to finish
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Count rows
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM COUNTER", Integer.class);

        // Initial count was 3
        // If two more rows are inserted, anomaly occurs
        if (count >= 5) {
            System.err.println("SerializationAnomaly was occurred");
        }
    }

    private void checkSerializationError(Exception ex) {
        String message = ex.getMessage();
        if (message.contains("could not serialize access")) {
            System.out.println("SerializationAnomaly was prevented");
        }
    }


}
