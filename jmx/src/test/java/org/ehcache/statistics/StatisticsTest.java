/*
 * Copyright Terracotta, Inc.
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

package org.ehcache.statistics;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.lang.management.ManagementFactory;
import java.net.URI;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.ehcache.StandaloneCache;
import org.ehcache.StandaloneCacheBuilder;
import org.ehcache.management.StatisticsMBeanRegister;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Hung Huynh
 *
 */
public class StatisticsTest {
  private final MBeanServer               mbeanServer = ManagementFactory.getPlatformMBeanServer();
  private final String                    cacheName   = "myCache";
  private StandaloneCache<Number, String> cache;
  private URI                             uri;
  private ObjectName                      mbeanName;
  private long                            capacity    = 10;

  @Before
  public void setup() throws Exception {
    uri = new URI("NullManager");
    cache = StandaloneCacheBuilder.newCacheBuilder(Number.class, String.class).withCapacity(capacity)
        .build();
    cache.init();
    mbeanName = StatisticsMBeanRegister.register(cache, uri, cacheName);
  }

  @After
  public void tearDown() throws Exception {
    StatisticsMBeanRegister.unregister(uri, cacheName);
  }

  @Test
  public void testStatsMBeanRegistration() throws Exception {
    // assert mbean existed
    assertThat((Long) mbeanServer.getAttribute(mbeanName, "CachePuts"), equalTo(0L));

    // remove cache and assert its mbean is gone
    StatisticsMBeanRegister.unregister(uri, cacheName);
    try {
      assertThat((Long) mbeanServer.getAttribute(mbeanName, "CachePuts"), equalTo(0L));
      fail();
    } catch (InstanceNotFoundException e) {
      // expected
    }
  }

  @Test
  @Ignore("issue-151")
  public void testEvict() throws Exception {
    assertThat(cache.getRuntimeConfiguration().getCapacityConstraint(),
        equalTo((Comparable<Long>) capacity));

    for (int i = 0; i < capacity + 1; i++) { 
      cache.put(i, "" + i);
    }
    
    CacheStatistics stats = cache.getStatistics();
    assertThat(stats.getCacheEvictions(), equalTo(1L));
  }

  @Test
  public void testStatistics() throws Exception {
    cache.put(1, "one"); // put
    cache.put(2, "two"); // put
    cache.put(3, "three"); // put
    cache.putIfAbsent(3, "three"); // get(hit)
    cache.putIfAbsent(4, "four"); // get(miss) + put
    cache.replace(1, "uno"); // get(hit) + put
    cache.replace(100, "blah"); // get(miss)
    cache.replace(2, "two", "dos"); // get(hit) + put
    cache.replace(3, "blah", "tres"); // get(miss)

    cache.get(1); // get(hit)
    cache.get(2); // get(hit)
    cache.get(3); // get(hit)

    cache.get(-2); // get(miss)

    cache.remove(1); // remove

    long expectedPuts = 6;
    long expectedGets = 10;
    long expectedHits = 6;
    long expectedMisses = 4;
    long expectedRemovals = 1;
    float hitPercentage = (float) expectedHits / expectedGets * 100;
    float misssPercentage = (float) expectedMisses / expectedGets * 100;

    CacheStatistics stats = cache.getStatistics();

    assertThat(stats.getCachePuts(), equalTo(expectedPuts));
    assertThat(stats.getCacheGets(), equalTo(expectedGets));
    assertThat(stats.getCacheHits(), equalTo(expectedHits));
    assertThat(stats.getCacheMisses(), equalTo(expectedMisses));
    assertThat(stats.getCacheRemovals(), equalTo(expectedRemovals));
    assertThat(stats.getCacheHitPercentage(), equalTo(hitPercentage));
    assertThat(stats.getCacheMissPercentage(), equalTo(misssPercentage));

    assertThat((Long) mbeanServer.getAttribute(mbeanName, "CachePuts"), equalTo(expectedPuts));
    assertThat((Long) mbeanServer.getAttribute(mbeanName, "CacheGets"), equalTo(expectedGets));
    assertThat((Long) mbeanServer.getAttribute(mbeanName, "CacheHits"), equalTo(expectedHits));
    assertThat((Long) mbeanServer.getAttribute(mbeanName, "CacheMisses"), equalTo(expectedMisses));
    assertThat((Long) mbeanServer.getAttribute(mbeanName, "CacheRemovals"),
        equalTo(expectedRemovals));
    assertThat((Float) mbeanServer.getAttribute(mbeanName, "CacheHitPercentage"),
        equalTo(hitPercentage));
    assertThat((Float) mbeanServer.getAttribute(mbeanName, "CacheMissPercentage"),
        equalTo(misssPercentage));
  }
}
