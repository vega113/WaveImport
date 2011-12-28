/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.walkaround.util.server;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helps retry datastore transactions.
 *
 * @author ohler@google.com (Christian Ohler)
 */
public class RetryHelper {

  @SuppressWarnings("unused")
  private static final Logger log = Logger.getLogger(RetryHelper.class.getName());

  public static class PermanentFailure extends Exception {
    public PermanentFailure() {
    }

    public PermanentFailure(String message) {
      super(message);
    }

    public PermanentFailure(Throwable cause) {
      super(cause);
    }

    public PermanentFailure(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class RetryableFailure extends Exception {
    public RetryableFailure() {
    }

    public RetryableFailure(String message) {
      super(message);
    }

    public RetryableFailure(Throwable cause) {
      super(cause);
    }

    public RetryableFailure(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public interface Body<R> {
    R run() throws RetryableFailure, PermanentFailure;
  }

  public interface VoidBody {
    void run() throws RetryableFailure, PermanentFailure;
  }

  public interface RetryStrategy {
    /**
     * Returns how long to sleep before retrying after the given exception has
     * occurred.  Negative values are not permitted.  This should throw
     * PermanentFailure if no more retries should be attempted.
     *
     * @param numRetries how many times the job has already been retried
     *        (not counting the initial attempt)
     * @param millisSoFar how much total time has elapsed so far running this job
     * @param exception the exception that was thrown
     */
    long delayMillisBeforeRetry(int numRetries, long millisSoFar,
        RetryableFailure exception) throws PermanentFailure;
  }

  public static RetryStrategy backoffStrategy(final long startDelayMillis,
      final long maxDelayMillis, final long maxTotalTime) {
    return new RetryStrategy() {
      Random random = new Random();
      long randomLong(long limit) {
        return (long) (random.nextDouble() * limit);
      }
      @Override public long delayMillisBeforeRetry(int numRetries, long millisSoFar,
          RetryableFailure e) throws PermanentFailure {
        long nextDelay = randomLong(
            Math.min(maxDelayMillis, startDelayMillis * (1 << numRetries))
            + 1);
        if (millisSoFar + nextDelay < maxTotalTime) {
          return nextDelay;
        } else {
          throw new PermanentFailure("Retry timeout exceeded: millisSoFar=" + millisSoFar
              + ", nextDelay=" + nextDelay + ", maxTotalTime=" + maxTotalTime, e);
        }
      }
    };
  }

  public static final RetryHelper NO_RETRY = new RetryHelper(new RetryStrategy() {
    @Override public long delayMillisBeforeRetry(int numRetries, long millisSoFar,
        RetryableFailure exception) throws PermanentFailure {
      throw new PermanentFailure("Retryable failure with NO_RETRY strategy", exception);
    }
  });

  private final RetryStrategy retryStrategy;

  public RetryHelper(RetryStrategy retryStrategy) {
    Preconditions.checkNotNull(retryStrategy, "Null retryStrategy");
    this.retryStrategy = retryStrategy;
  }

  public RetryHelper() {
    this(backoffStrategy(5, 200, 15000));
  }

  private <R> R runBodyOnce(Body<R> b) throws RetryableFailure, PermanentFailure {
    Stopwatch stopwatch = new Stopwatch().start();
    log.info("Running body " + b);
    boolean normalExit = false;
    try {
      R result = b.run();
      normalExit = true;
      return result;
    } finally {
      long duration = stopwatch.elapsedMillis();
      log.info("Body exited " + (normalExit ? "normally" : "abnormally")
          + ", run time: " + duration + "ms");
    }
  }

  public <R> R run(Body<R> b) throws PermanentFailure {
    Stopwatch stopwatch = new Stopwatch().start();
    for (int retries = 0; true; retries++) {
      try {
        return runBodyOnce(b);
      } catch (RetryableFailure e) {
        long elapsedMillis = stopwatch.elapsedMillis();
        log.log(Level.WARNING, "Problem on retry " + retries + ", millis elapsed so far: "
            + elapsedMillis, e);
        long delayMillis = retryStrategy.delayMillisBeforeRetry(retries, elapsedMillis, e);
        if (delayMillis < 0) {
          log.warning("Negative delay: " + delayMillis);
          delayMillis = 100;
        }
        log.info("Sleeping for " + delayMillis + " millis");
        try {
          Thread.sleep(delayMillis);
        } catch (InterruptedException e2) {
          Thread.currentThread().interrupt();
          throw new PermanentFailure("Interrupted while waiting to retry; "
              + retries + " tries total, " + stopwatch.elapsedMillis() + " millis elapsed", e2);
        }
      }
    }
  }


  public void run(final VoidBody b) throws PermanentFailure {
    run(new Body<Void>() {
          @Override public Void run() throws PermanentFailure, RetryableFailure {
            b.run();
            return null;
          }
          @Override public String toString() {
            return "VoidBodyWrapper(" + b + ")";
          }
        });
  }

}
