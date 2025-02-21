Необходимо используя Spring boot разработать приложение, которое умеет в фоне выполнять продолжительные задачи.
Пользователь может инициировать такую задачу и получить в ответ ее ид.
Далее пользователь может попытаться получить результат по заданному ид.
Две одинаковых задачи не могут быть запущенны одновременно, при этом может быть несколько инстансов приложения.
Приложение должно уметь восстанавливать работу если было перезапущенно с того же места где последний этап задачи был выполнен.
Нужны тесты на основные сценарии

Для примера задачи можно использовать следуюющее:

```
class TestJob {
  Stream<Integer> run(int min, int max, int count) {
    AtomicInteger counter = new AtomicInteger(0);
    return Stream
      .generate(() -> {
        counter.incrementAndGet();
        int random = (int)(Math.random() * max + min);
        return random;
      })
      .takeWhile(n -> counter.get() < count);
  }
}
```
