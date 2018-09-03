package com.cv4j.netdiscovery.extra.queue.redis;

import com.cv4j.netdiscovery.core.domain.Request;
import com.cv4j.netdiscovery.core.queue.AbstractQueue;
import com.cv4j.netdiscovery.core.queue.filter.DuplicateFilter;
import com.google.gson.Gson;
import com.safframework.tony.common.utils.Preconditions;
import org.apache.commons.codec.digest.DigestUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Created by tony on 2018/1/1.
 */
public class RedisQueue extends AbstractQueue implements DuplicateFilter{

    private static final String QUEUE_PREFIX = "queue_";

    private static final String SET_PREFIX = "set_";

    private static final String ITEM_PREFIX = "item_";

    protected JedisPool pool;

    protected Gson gson = new Gson();

    public RedisQueue(String host) {

        this(new JedisPool(new JedisPoolConfig(), host));
    }

    public RedisQueue(JedisPool pool) {

        this.pool = pool;
        setFilter(this);
    }

    @Override
    public boolean isDuplicate(Request request) {

        if (request.isCheckDuplicate()) {

            Jedis jedis = pool.getResource();
            try {
                return jedis.sadd(getSetKey(request), request.getUrl()) == 0;
            } finally {
                pool.returnResource(jedis);
            }
        } else {

            Jedis jedis = pool.getResource();
            try {
                jedis.sadd(getSetKey(request), request.getUrl());
            } finally {
                pool.returnResource(jedis);
            }

            return false;
        }

    }

    @Override
    protected void pushWhenNoDuplicate(Request request) {

        Jedis jedis = pool.getResource();
        try {
            jedis.rpush(getQueueKey(request.getSpiderName()), request.getUrl());

            if (hasExtraRequestInfo(request)) {

                String field = DigestUtils.shaHex(request.getUrl());
                String value = gson.toJson(request);
                jedis.hset((ITEM_PREFIX + request.getUrl()), field, value);
            }

        } finally {
            jedis.close();
        }

    }

    private boolean hasExtraRequestInfo(Request request) {

        if (request == null) {
            return false;
        }

        if (Preconditions.isNotBlank(request.getHeader())) {
            return true;
        }

        if (Preconditions.isNotBlank(request.getCharset())) {
            return true;
        }

        if (Preconditions.isNotBlank(request.getExtras())) {
            return true;
        }

        if (request.getPriority()>0) {
            return true;
        }

        return false;
    }

    @Override
    public synchronized Request poll(String spiderName) {

        Jedis jedis = pool.getResource();
        try {
            String url = jedis.lpop(getQueueKey(spiderName));
            if (url == null) {
                return null;
            }

            String key = ITEM_PREFIX + url;
            String field = DigestUtils.shaHex(url);
            byte[] bytes = jedis.hget(key.getBytes(), field.getBytes());

            if (bytes != null) {

                Request o = gson.fromJson(new String(bytes),Request.class);
                return o;
            }

            Request request = new Request(url);
            return request;
        } finally {
            pool.returnResource(jedis);
        }
    }

    @Override
    public int getLeftRequests(String spiderName) {

        Jedis jedis = pool.getResource();
        try {
            Long size = jedis.llen(getQueueKey(spiderName));
            return size.intValue();
        } finally {
            pool.returnResource(jedis);
        }
    }

    @Override
    public int getTotalRequests(String spiderName) {
        Jedis jedis = pool.getResource();
        try {
            Long size = jedis.scard(getSetKey(spiderName));
            return size.intValue();
        } finally {
            pool.returnResource(jedis);
        }
    }

    /**
     * RedisQueue 无须使用该方法来获取Queue中总共的Request
     * @return
     */
    @Override
    public int getTotalRequestsCount() {
        return 0;
    }

    protected String getQueueKey(String spiderName) {
        return QUEUE_PREFIX + spiderName;
    }

    protected String getSetKey(Request request) {
        return SET_PREFIX + request.getSpiderName();
    }

    protected String getSetKey(String spiderName) {
        return SET_PREFIX + spiderName;
    }

    protected String getItemKey(Request request) {
        return ITEM_PREFIX + request.getUrl();
    }

    protected String getItemKey(String url) {
        return ITEM_PREFIX + url;
    }
}
