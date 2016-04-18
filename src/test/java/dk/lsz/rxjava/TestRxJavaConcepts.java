package dk.lsz.rxjava;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;
import rx.schedulers.Schedulers;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Created by lars on 22-02-16.
 */
public class TestRxJavaConcepts {
    private static final Logger log = LoggerFactory.getLogger(TestRxJavaConcepts.class);

    @Test
    public void simple() {
        Observable<String> strings = Observable.create(s -> {
            log.info("connecting subscriber");
            try {
                s.onStart();
                s.onNext("produce 1");
                s.onNext("produce 2");
                s.onCompleted();
            } catch (Exception e) {
                s.onError(e);
            }
        });

        strings.subscribe(
                s -> log.info("on next: {}", s),  // behandling af hvert element
                e -> log.error("boom", e),  // hvis der sker en fejl
                () -> log.info("completed no errors") // når strømmen slutter normalt
        );

        strings.subscribe(
                log::info,
                e -> log.error("boom", e),
                () -> log.info("completed no errors")
        );
    }


    @Test
    public void multicast() throws InterruptedException {
        ConnectableObservable<String> multicast = Observable.<String>create(s -> {
            log.info("connecting subscriber");
            try {
                s.onStart();
                s.onNext("produce 1");
                s.onNext("produce 2");
                s.onCompleted();
            } catch (Exception e) {
                s.onError(e);
            }
        }).publish();

        multicast.subscribe(
                log::info,
                e -> log.error("boom", e),
                () -> log.info("1: completed no errors")
        );

        multicast.subscribe(
                log::info,
                e -> log.error("boom", e),
                () -> log.info("2: completed no errors")
        );

        log.info("ready to connect");

        multicast.connect();

        Thread.sleep(1000);
    }


    @Test
    public void operations() {
        Observable.from("rx-Java is so cool! Not-only-cool .. super-COOL!".split("\\s"))
                .map(String::toUpperCase)
                .flatMap(s -> Observable.from(s.split("-")))
                .doOnNext(log::info)    // log elementerne, når de passerer forbi
                .filter(s -> s.contains("COOL"))
                .count()
                .subscribe(n -> log.info("cool was found {} time(s)", n));
    }

    @Test
    public void async_1() throws InterruptedException {
        final Random random = new Random(11345);

        // simulerer et webservice-kald til en langsom og dyr asynkron opeartion
        Observable<String> ws1 = Observable.<String>create(s -> {
            try {
                log.info("ws start");
                s.onStart();
                // tænke, tænke, tænke ...
                Thread.sleep(random.nextInt(1000));
                // 50% chance for fejl undervejs
                if (random.nextBoolean())
                    throw new Exception("oh-no the service blew up!");

                for (int i = random.nextInt(5); i > 0; i--) {
                    s.onNext("ws-element " + i);
                }

                log.info("ws end");
                s.onCompleted();
            } catch (Throwable e) {
                s.onError(e);
            }
        }).subscribeOn(Schedulers.io());

        ws1.subscribe(log::info, err -> log.error("bum: {}", err.getMessage()));
        ws1.subscribe(log::info, err -> log.error("bum: {}", err.getMessage()));
        ws1.subscribe(log::info, err -> log.error("bum: {}", err.getMessage()));
        log.info("ws calls done");

        Thread.sleep(5000);
    }

    @Test
    public void async_2() throws InterruptedException {
        // en mock asynkron proces
        Func1<Integer, Observable<String>> wsKald = param -> Observable.<String>create(s -> {
            s.onStart();
            s.onNext("called with " + param);
            s.onCompleted();
        }).subscribeOn(Schedulers.io());

        Func1<Long, Observable<Integer>> multi = param -> Observable.<Integer>create(s -> {
            s.onStart();
            IntStream.range(1, param.intValue()).forEach(s::onNext);
            s.onCompleted();
        }).subscribeOn(Schedulers.io());

        // asynkron tæller, hvert ½ sekund
        Observable<Long> ticker = Observable.interval(500, TimeUnit.MILLISECONDS);

        ticker.take(10)
                .flatMap(multi)
                .flatMap(wsKald)
                .toBlocking()
                .subscribe(log::info);
    }

    @Test
    public void alsoForCombiningStreams() {
        Observable<Integer> range = Observable.range(1, 5);

        Observable<String> repeat = Observable.just("a", "b", "c").repeat();

        Observable.concat(range, repeat)
                .take(10)
                .subscribe(value -> log.info("concat: {}", value));

        Observable
                .zip(range, repeat, repeat.skip(1),
                        (i, s1, s2) -> i + "-" + s1 + "-" + s2)
                .subscribe(value -> log.info("zip: {}", value));

        range.
                flatMap(i -> Observable.just(i * 10, i * 20))
                .subscribe(value -> log.info("flatMap: {}", value));
    }

    @Test
    public void async_3() throws InterruptedException {
        // ticker, som sender tæller op asynkront
        Observable<Long> o1 = Observable.interval(100, TimeUnit.MILLISECONDS);
        Observable<Long> o2 = Observable.interval(300, TimeUnit.MILLISECONDS);

        Observable.zip(o1, o2, (e1, e2) -> e1 + e2)
                .map(String::valueOf)
                .subscribe(log::info);

        Thread.sleep(2000);
    }

    @Test
    public void example() {
        // db-kald som funktion der returner en Observable
        Func1<Integer, Observable<Integer>> db1 = param -> Observable.range(1, param);

        // ws-kald som funktion der returner en Observable
        Func1<Integer, Observable<Integer>> ws1 = param -> Observable.just(param);

        // ws-kald som funktion der returner en Observable
        Func1<Integer, Observable<Integer>> ws2 = param -> Observable.just(param);

        // ws-kald som funktion der returner en Observable
        Func1<List<Integer>, Observable<Object>> ws3 = param -> Observable.empty();

        db1.call(5)
                .flatMap(row -> {
                    if (row % 2 == 0) {
                        // ws2 kaldes kun, hvis ws1 ikke var tom
                        return Observable.concat(ws1.call(row), ws2.call(row)).first();
                    } else {
                        // ws1 og ws2 udføres parallelt
                        return Observable.zip(ws1.call(10), ws2.call(20), (e1, e2) -> e1 + e2);
                    }
                })
                .onErrorResumeNext(err -> {
                    log.error("error - resume next ", err);
                    return Observable.empty();
                })
                .doOnNext(n -> log.info("element {}", n))
                // samle/batche 2 elementer i en liste (men højst vente 1 sekund)
                .buffer(2, 1, TimeUnit.SECONDS)
                // output sendes til en ws3
                .flatMap(ws3)
                // subscribe starter kæden
                .subscribe();
    }
}
