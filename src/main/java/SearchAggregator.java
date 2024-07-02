import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchAggregator {

    public static void main(String[] args) {

        int resultLimit = 40;
        String query = "how to fix my computer";

        // connecting to mongodb
        String uri = "mongodb://localhost:27017";
        MongoClientURI clientURI = new MongoClientURI(uri);
        MongoClient mongoClient = new MongoClient(clientURI);

        MongoDatabase database = mongoClient.getDatabase("results");
        MongoCollection<Document> collection = database.getCollection("search_results");

        System.out.println("connected");

        // each search engine stores their results individually and add them to the collection

        // fetch results from DuckDuckGo
        List<Document> duckDuckGoResults = fetchResultsFromDuckDuckGo(query,collection, resultLimit);

        // fetch results from Bing
        List<Document> bingResults = fetchResultsFromBing(query,collection, resultLimit);

        // store similar website to display in the console
        List<String> similarWebsites = calculateSimilarWebsites(duckDuckGoResults, bingResults);

        // displaying similar/different result stats
        int countSimilar = 0;
        System.out.println("\nSimilar websites:");
        for (String website : similarWebsites) {
            System.out.println(website);
            countSimilar++;
        }
        System.out.println("\n=> "+countSimilar+" similar results");

        int countDiff = resultLimit-countSimilar;
        System.out.println("\nDifferent websites: " + countDiff);


        // Close the MongoDB connection
        mongoClient.close();
    }

    //DDG: 30 => max result of each page for duckduckgo

    private static List<Document> fetchResultsFromDuckDuckGo(String query, MongoCollection<Document> collection, int resultLimit) {
        List<Document> results = new ArrayList<>();
        String url = "https://html.duckduckgo.com/html/?q=" + query;

        try {
            // retrieving html elements containing search results
            Elements links = Jsoup.connect(url).get().select("a.result__a");
            int count = 0;
            for (Element link : links) {
                if(count >= resultLimit) break;

                //adding each element as a document in the collection
                Document doc = new Document("rel", link.attr("rel"))
                        .append("search_engine", "duckduckgo")
                        .append("href", link.attr("href"))
                        .append("description", link.text());
                results.add(doc);
                count++;
            }

            // insert the list of docs into the collection
            collection.insertMany(results);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return results;
    }

//BING: 27
    private static List<Document> fetchResultsFromBing(String query, MongoCollection<Document> collection, int resultLimit) {
        List<Document> results = new ArrayList<>();
        String url = "https://www.bing.com/search?q=" + query;

        try {
            Elements links = Jsoup.connect(url).get().select("a");
            int count = 0;
            for (Element link : links) {
                if (count >= resultLimit) break;
                String href = link.attr("href");
                if (href.startsWith("http")) {
                    Document doc = new Document("href", link.attr("href"))
                            .append("search_engine", "bing");

                results.add(doc);
                count++;
                }
            }

            // Insert the list of links into the collection
            collection.insertMany(results);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return results;
    }

    // calculating similar websites by comparing the urls from each search engine
    private static List<String> calculateSimilarWebsites(List<Document> duckDuckGoResults, List<Document> bingResults) {
        Set<String> duckDuckGoUrls = new HashSet<>();
        Set<String> bingUrls = new HashSet<>();

        for (Document doc : duckDuckGoResults) {
            duckDuckGoUrls.add(doc.getString("href"));
        }

        for (Document doc : bingResults) {
            bingUrls.add(doc.getString("href"));
        }

        // finding common URLs
        duckDuckGoUrls.retainAll(bingUrls);

        return new ArrayList<>(duckDuckGoUrls);
    }
}
