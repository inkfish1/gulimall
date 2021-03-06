package com.xmh.gulimall.search;

import com.alibaba.fastjson.JSON;
import com.xmh.gulimall.search.config.GulimallElasticSearchConfig;
import lombok.Data;
import lombok.ToString;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.Avg;
import org.elasticsearch.search.aggregations.metrics.AvgAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GulimallSearchApplicationTests {

    @Autowired
    private RestHighLevelClient client;

    @Data
    @ToString
    public static class Account {
        private int account_number;
        private int balance;
        private String firstname;
        private String lastname;
        private int age;
        private String gender;
        private String address;
        private String employer;
        private String email;
        private String city;
        private String state;
    }

    @Test
    public void searchData() throws IOException {
        //1?????????????????????
        SearchRequest searchRequest = new SearchRequest();
        //????????????
        searchRequest.indices("bank");
        //??????DSL???????????????
        //SearchSourceBuilder sourceBuilder ?????????????????????
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        //1.1??????????????????????????????????????????mill???
        //sourceBuilder.query();
        //sourceBuilder.from();
        //sourceBuilder.size();
        //sourceBuilder.aggregation();
        sourceBuilder.query(QueryBuilders.matchQuery("address", "mill"));
        searchRequest.source(sourceBuilder);
        System.out.println(sourceBuilder.toString()); //??????????????????

        //1.2???????????????????????????????????????
        TermsAggregationBuilder ageAgg = AggregationBuilders.terms("ageAgg").field("age").size(10);
        sourceBuilder.aggregation(ageAgg);

        //1.3?????????????????????
        AvgAggregationBuilder balanceAvg = AggregationBuilders.avg("balanceAvg").field("balance");
        sourceBuilder.aggregation(balanceAvg);


        //2???????????????
        SearchResponse search = client.search(searchRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);


        //3???????????????
        System.out.println(search.toString());
        //3.1??????????????????????????????
        SearchHits hits = search.getHits();
        SearchHit[] searchHits = hits.getHits();
        for (SearchHit hit : searchHits) {
            //hit.getIndex();hit.getType();hit.getId();  //????????????????????????
            String sourceAsString = hit.getSourceAsString();  //?????????????????????????????????json?????????
            Account account = JSON.parseObject(sourceAsString, Account.class);
            System.out.println("account" + account);
        }

        //3.2???????????????????????????????????????
        Aggregations aggregations = search.getAggregations();
        Terms ageAgg1 = aggregations.get("ageAgg");//???????????????
        for (Terms.Bucket bucket : ageAgg1.getBuckets()) {
            String keyAsString = bucket.getKeyAsString();
            System.out.println("?????????" + keyAsString + "????????????" + bucket.getDocCount() + "???");
        }

        Avg balanceAvg1 = aggregations.get("balanceAvg");
        double value = balanceAvg1.getValue();
        System.out.println("??????????????????" + value);

    }

    @Test
    public void indexData() throws IOException {
        IndexRequest indexRequest = new IndexRequest("users");//??????users???????????????
        indexRequest.id("1");//?????????id

        User user = new User();//??????????????????
        user.setUsername("??????");
        user.setAge(18);
        user.setGender("???");
        //??????????????????json?????????????????????
        String jsonString = JSON.toJSONString(user);
        indexRequest.source(jsonString, XContentType.JSON);

        //??????????????????
        IndexResponse index = client.index(indexRequest, GulimallElasticSearchConfig.COMMON_OPTIONS);

        //???????????????????????????
        System.out.println(index);
    }

    @Data
    class User{
        private String username;
        private String gender;
        private Integer age;
    }

    @Test
    public void contextLoads(){
        System.out.println(client);
    }

    @Test
    public void test1(){
        String str = new StringBuilder("ja").append("va").toString();
        String str1 = "java";
        String str3 = new String("java");
        System.out.println(str.intern() == str);
        System.out.println(str1 == str1.intern());


    }

}
