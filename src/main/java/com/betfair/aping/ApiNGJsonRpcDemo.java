package com.betfair.aping;

import com.betfair.aping.api.ApiNgJsonRpcOperations;
import com.betfair.aping.api.ApiNgOperations;
import com.betfair.aping.entities.*;
import com.betfair.aping.enums.*;
import com.betfair.aping.exceptions.APINGException;
import com.google.gson.Gson;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * This is a demonstration class to show a quick demo of the new Betfair API-NG.
 * When you execute the class will: <li>find a market (next horse race in the
 * UK)</li> <li>get prices and runners on this market</li> <li>place a bet on 1
 * runner</li> <li>handle the error</li>
 */
public class ApiNGJsonRpcDemo {
    Gson gson = new Gson();
    DecimalFormat df = new DecimalFormat("0.00");
    Calendar cal = Calendar.getInstance();
    SimpleDateFormat dtf = new SimpleDateFormat("yyyyMMdd.HHmmss");
    private ApiNgOperations jsonOperations = ApiNgJsonRpcOperations.getInstance();

    private static Properties getProps() {
        return ApiNGDemo.getProp();
    }

    public void start() {
        try {
            MarketFilter marketFilter;
            Set<String> eventIds = new HashSet<String>();
            BackUnderMarketAlgo backUnderMarketAlgo = new BackUnderMarketAlgo();

            marketFilter = getMarketFilter();

            List<EventResult> eventResults = getEvents(marketFilter);
            for (EventResult er : eventResults) {
                eventIds.add(er.getEvent().getId());
            }
            marketFilter.setEventIds(eventIds);

            List<MarketCatalogue> marketCatalogueResult = getMarketCatalogues(marketFilter);

            List<Event> events = assignMarketsToEvents(eventResults, marketCatalogueResult);

            printEvents(events);

            getMarketBooks(marketCatalogueResult);
            printMarketBooks(events);
            for (int i = 0; i < 100; i++) {
                System.out.println(dtf.format(cal.getTime()) + " --------------------Iteration " + i + " Start--------------------");
                for (Event event : events) {
                    backUnderMarketAlgo.process(event);
                }
                System.out.println(dtf.format(cal.getTime()) + " --------------------Iteration " + i + " End--------------------");
                Thread.sleep(5000);
                getMarketBooks(marketCatalogueResult);
            }
        } catch (APINGException apiExc) {
            System.out.println(apiExc.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printMarketBooks(List<Event> events) {
        System.out.println("Full MarketBook Listing Start");
        for (Event e : events) {
            System.out.println(e.getName());
            for (MarketType mt : e.getMarket().keySet()) {
                System.out.println("  " + mt + ": " + gson.toJson(e.getMarket().get(mt)));
            }
        }
        System.out.println("Full MarketBook Listing End");
    }

    private void getMarketBooks(List<MarketCatalogue> marketCatalogueResult) throws APINGException {
        PriceProjection priceProjection = new PriceProjection();
        Set<PriceData> priceData = new HashSet<PriceData>();
        priceData.add(PriceData.EX_BEST_OFFERS);
        priceProjection.setPriceData(priceData);
        OrderProjection orderProjection = OrderProjection.ALL;
        MatchProjection matchProjection = null;
        String currencyCode = null;
        int batchRequestCost = 0;
        int totalRequests = 0;
        final int DATA_LIMIT = 200;
        final int QUERY_COST = 5;

        List<String> marketIds = new ArrayList<String>();
        List<String> marketIdsBatch = new ArrayList<String>();

        for (MarketCatalogue mc : marketCatalogueResult) {
            marketIds.add(mc.getMarketId());
        }

        for (String id : marketIds) {
            marketIdsBatch.add(id);
            batchRequestCost += QUERY_COST;
            totalRequests++;
            if ((batchRequestCost + QUERY_COST >= DATA_LIMIT) || totalRequests == marketIds.size()) {
                List<MarketBook> marketBookReturn = jsonOperations.listMarketBook(marketIdsBatch, priceProjection,
                        orderProjection, matchProjection, currencyCode);
                for (MarketCatalogue mc : marketCatalogueResult) {
                    for (MarketBook mb : marketBookReturn) {
                        if (mc.getMarketId().equals(mb.getMarketId())) {
                            mc.setMarketBook(mb);
                        }
                    }
                }
                marketIdsBatch.clear();
                batchRequestCost = 0;
            }
        }
    }

    private void printEvents(List<Event> events) {
        System.out.println("Full Event Listing Start");
        for (Event e : events) {
            System.out.println(gson.toJson(e));
        }
        System.out.println("Full Event Listing End");
    }

    private List<Event> assignMarketsToEvents(List<EventResult> eventResults, List<MarketCatalogue> mks) {
        List<Event> events = new ArrayList<Event>();
        for (MarketCatalogue mk : mks) {
            for (EventResult er : eventResults) {
                if (mk.getEvent().getId().equals(er.getEvent().getId())) {
                    er.getEvent().getMarket().put(mk.getDescription().getMarketType(), mk);
                    mk.getDescription().setRules("");
                }
            }
        }
        for (EventResult er : eventResults) {
            events.add(er.getEvent());
        }
        return events;
    }

    private List<EventResult> getEvents(MarketFilter marketFilter) throws APINGException {
        System.out.println("3.1 (listEvents) Get all events for " + gson.toJson(marketFilter.getMarketTypeCodes()) + "...");

        List<EventResult> events = jsonOperations.listEvents(marketFilter);

        System.out.println("3.2 (listEvents) Events Returned: " + events.size() + "\n");

        return events;
    }

    private MarketFilter getMarketFilter() throws APINGException {
        MarketFilter marketFilter = new MarketFilter();
        Calendar cal = null;
        TimeRange time = new TimeRange();
        Set<String> countries = new HashSet<String>();
        Set<String> typesCode = new HashSet<String>();
        typesCode = gson.fromJson(getProps().getProperty("MARKET_TYPES"), typesCode.getClass());

        cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -120);
        time.setFrom(cal.getTime());

        cal = Calendar.getInstance();
        String timeBeforeStart = getProps().getProperty("TIME_BEFORE_START");
        if (timeBeforeStart.length() > 0) {
            cal.add(Calendar.MINUTE, Integer.valueOf(timeBeforeStart));
            time.setTo(cal.getTime());
        }

        marketFilter.setMarketStartTime(time);
        marketFilter.setMarketCountries(countries);
        marketFilter.setMarketTypeCodes(typesCode);
        marketFilter.setTurnInPlayEnabled(true);
        marketFilter.setEventTypeIds(getEventTypeIds());
        marketFilter.setCompetitionIds(getCompetitionIds());

        return marketFilter;
    }

    private List<MarketCatalogue> getMarketCatalogues(MarketFilter marketFilter) throws APINGException {

        Set<MarketProjection> marketProjection = new HashSet<MarketProjection>();
        marketProjection.add(MarketProjection.COMPETITION);
        marketProjection.add(MarketProjection.EVENT);
        marketProjection.add(MarketProjection.MARKET_DESCRIPTION);
        marketProjection.add(MarketProjection.RUNNER_DESCRIPTION);
        marketProjection.add(MarketProjection.MARKET_START_TIME);

        System.out.println("4.1 (listMarketCataloque) Get all markets for " + gson.toJson(marketFilter.getMarketTypeCodes()) + "...");

        String maxResults = getProps().getProperty("MAX_RESULTS");

        List<MarketCatalogue> mks = jsonOperations.listMarketCatalogue(marketFilter, marketProjection, MarketSort.FIRST_TO_START, maxResults);

        System.out.println("4.2. Print Event, Market Info, name and runners...\n");
        printMarketCatalogue(mks);
        return mks;
    }

    private Set<String> getCompetitionIds() throws APINGException {
        MarketFilter marketFilter = new MarketFilter();
        Set<String> competitionIds = new HashSet<String>();
        Set<String> competitions = new HashSet<String>();
        competitions = gson.fromJson(getProps().getProperty("COMPETITIONS"), competitions.getClass());

        System.out.println("2.1.(listCompetitions) Get all Competitions...");
        List<CompetitionResult> c = jsonOperations.listCompetitions(marketFilter);
        System.out.println("2.2. Extract Competition Ids...");
        for (CompetitionResult competitionResult : c) {
            if (competitions.contains(competitionResult.getCompetition().getName())) {
                System.out.println("2.3. Competition Id for " + competitionResult.getCompetition().getName() + " is: " + competitionResult.getCompetition().getId());
                competitionIds.add(competitionResult.getCompetition().getId().toString());
            }
        }
        System.out.println();
        return competitionIds;
    }

    private Set<String> getEventTypeIds() throws APINGException {
        MarketFilter marketFilter = new MarketFilter();
        Set<String> eventTypeIds = new HashSet<String>();
        Set<String> eventTypes = new HashSet<String>();

        eventTypes = gson.fromJson(getProps().getProperty("EVENT_TYPES"), eventTypes.getClass());

        System.out.println("1.1.(listEventTypes) Get all Event Types...");
        List<EventTypeResult> r = jsonOperations.listEventTypes(marketFilter);
        System.out.println("1.2. Extract Event Type Ids...");
        for (EventTypeResult eventTypeResult : r) {
            if (eventTypes.contains(eventTypeResult.getEventType().getName())) {
                System.out.println("1.3. EventTypeId for " + eventTypeResult.getEventType().getName() + " is: " + eventTypeResult.getEventType().getId() + "\n");
                eventTypeIds.add(eventTypeResult.getEventType().getId().toString());
            }
        }
        return eventTypeIds;
    }

    private void printMarketCatalogue(List<MarketCatalogue> mks) {
        for (MarketCatalogue mk : mks) {
            System.out.println("Event: " + mk.getEvent().getName() + ", Market Name: " + mk.getMarketName() + "; Id: " + mk.getMarketId() + "\n");
            List<RunnerCatalog> runners = mk.getRunners();
            if (runners != null) {
                for (RunnerCatalog rCat : runners) {
                    System.out.println("  Runner Name: " + rCat.getRunnerName() + "; Selection Id: " + rCat.getSelectionId());
                }
                System.out.println();
            }
        }
    }

    public Gson getGson() {
        return gson;
    }
}
