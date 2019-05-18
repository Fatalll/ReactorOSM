# Reactor OSM

Простой кеширующий веб-прокси-сервер к тайлам карты сервиса [OpenStreetMap](https://www.openstreetmap.org) 
на основе [Spring](https://spring.io/) [WebFlux](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html) и [Project Reactor](https://projectreactor.io/).
При запросах на сервер в первый раз осуществляется перенаправление запроса на https://a.tile.openstreetmap.org,
далее запрошенный тайл сохраняется на диске и последующие запросы этого тайла берутся с диска.

В случае множества одновременных запросов одного тайла, будет отправлен только один запрос на https://a.tile.openstreetmap.org, 
т.е. в случае существования уже активного запроса конкретного тайла, новый запрос этого тайла подписывается на ответ исполняющемуся запросу. 

До тех пор пока файл не запишется на диск, запрос считается активным и хранит кэшированный результат выполнения, так что новые запросы
этого тайла смогут его получить не делая запрос на https://a.tile.openstreetmap.org.

В качестве фреймворка был выбран Spring, т.к. в нем уже реализована поддержка реактивного стека, в частности поддержка Reactor.

