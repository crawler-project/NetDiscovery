package cn.netdiscovery.example;

import cn.netdiscovery.core.Spider;
import cn.netdiscovery.core.SpiderEngine;
import cn.netdiscovery.core.domain.Request;

/**
 * Created by tony on 2019-05-12.
 */
public class TestJob {

    public static void main(String[] args) {

        SpiderEngine spiderEngine = SpiderEngine.create();

        Request request = new Request("http://www.163.com").checkDuplicate(false);

        spiderEngine
                .addSpider(Spider.create().name("tony1").request(request))
                .httpd()
                .run();

        spiderEngine.addJob("tony1",request,"0 * * * * ?");
    }
}
