import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.NoSuchElementException;

import java.io.BufferedWriter;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.lang.Thread;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import org.openqa.selenium.TimeoutException;

// 1. Скопировать ссылку на карточку.
// 2. Открыть новое окно по ссылке.
// 3. В новом окне по ссылке скопировать значения текста в тегах названия магазина и цены
// 4. Записать это к карточке товара.
// 5. Закрыть окно, открыть следующее.
// 6. Дойдя до конца списка ссылок на карточки, открыть новое окно по следующей ссылке на страницу.
// 7. Закрыть старое окно, повторить пункт 1.
// 8. Если не ссылки на следующую страницу, закрыть окно.

import java.sql.*;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import java.io.*;
import java.util.*;

class ProductParseThread extends Thread {
    public final static ChromeOptions chromeOptions = new ChromeOptions()
            .addArguments("--headless")
            .addArguments("--log-level=3");
    private CountDownLatch latch;
    private String name;
    private String href;

    ProductParseThread(String productName, String productHref, CountDownLatch latch) {
        this.name = productName;
        this.href = productHref;
        this.latch = latch;
    }

    @Override
    public void run() {
        Product product = new Product();
        String link = this.href;
        WebDriver cardDriver = new ChromeDriver();
        cardDriver.get(link);

        WebDriverWait wait = new WebDriverWait(cardDriver, Duration.ofSeconds(5));
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//td/a")));
        } catch (TimeoutException e) {
            System.out.println("Не дождались загрузки продавцов");
        }

        Set<String> sellerLinks = new HashSet<>();

        while (true){
            List<WebElement> shops = cardDriver.findElements(By.xpath("//td/a"));
            for (WebElement shop : shops) {
                String shopHref = shop.getAttribute("href");
                if (shopHref != null && !shopHref.isEmpty() && !sellerLinks.contains(shopHref)) {
                    sellerLinks.add(shopHref);
                }
            }

            break; // убрать и вернуть ниже, только первые 5 продвацов берет для тестирования

//            try {
//                WebElement almatyOption = cardDriver.findElement(By.xpath("//a[@data-city-id='750000000']"));
//                almatyOption.click();
//                Thread.sleep(1000);
//            } catch (Exception ignored) {}
//
//
//            try {
//                WebElement nextBtn = cardDriver.findElement(By.xpath("//li[contains(text(), 'Следующая')]"));
//                if (nextBtn.getAttribute("class").contains("disabled")) {
//                    break;
//                }
//                nextBtn.click();
//                Thread.sleep(1000);
//            } catch (Exception e) {
//                break;
//            }
        }

        for (String shopHref : sellerLinks) {
            WebDriver shopDriver = new ChromeDriver();
            shopDriver.get(shopHref);
            String sellerName = "", phoneNumber = "", dateFrom = "", rating = "";

            try {
                sellerName = shopDriver.findElement(By.className("merchant-profile__title")).getText();
            } catch (NoSuchElementException ignored) {}

            try {
                phoneNumber = shopDriver.findElement(By.className("merchant-profile__contact-text")).getText();
            } catch (NoSuchElementException ignored) {}

            try {
                dateFrom = shopDriver.findElement(By.className("merchant-profile__register-date")).getText();
            } catch (NoSuchElementException ignored) {}

            try {
                WebElement ratingElement = shopDriver.findElement(By.cssSelector(".merchant-profile__rating.rating._seller"));
                Matcher matcher = Pattern.compile("_(\\d+)$").matcher(ratingElement.getAttribute("class"));
                if (matcher.find()) {
                    double ratingDouble = Double.parseDouble(matcher.group(1)) / 10.0;
                    rating = String.valueOf(ratingDouble);
                }
            } catch (NoSuchElementException ignored) {}

            shopDriver.quit();
            Deal deal = new Deal(sellerName.replaceAll(" в городе", ""), phoneNumber, dateFrom.replaceAll("В Kaspi Магазине с ", ""), rating);
            product.addDeal(deal);
        }
        parser.addProduct(product);
        sellerLinks.clear();
        cardDriver.quit();
        this.latch.countDown();
    }
}

class Deal {
    private String sellerName;
    private String phoneNumber;
    private String dateFrom;
    private String rating;

    public Deal(String sellerName, String phoneNumber, String dateFrom, String rating) {
        this.sellerName = sellerName;
        this.phoneNumber = phoneNumber;
        this.dateFrom = dateFrom;
        this.rating = rating;
    }

    public String getSellerName() {
        return this.sellerName;
    }

    public String getPhoneNumber() {
        return this.phoneNumber;
    }

    public String getDateFrom() {
        return this.dateFrom;
    }

    public String getRating() {
        return this.rating;
    }
}

class Product {
    private ArrayList<Deal> deals = new ArrayList<>();

    public Product() {
    }

    public void addDeal(Deal deal) {
        this.deals.add(deal);
    }

    public ArrayList<Deal> getDeals() {
        return this.deals;
    }

}

public class parser {

    public final static String categoryPath = "https://kaspi.kz/shop/c/beauty%20care/";

    public final static ChromeOptions chromeOptions = new ChromeOptions()
            .addArguments("--headless")
            .addArguments("--log-level=3");

    public static boolean nextFlag = false;

    static String exportFilePath = "deals_export.txt";

    public static ArrayList<Product> products = new ArrayList<>();

    public static void addProduct(Product product) {
        products.add(product);
    }

    public static void setNextFlag(boolean flag) {
        nextFlag = flag;
    }

    public static void writeProducts() throws IOException {
        FileWriter exportWriter = new FileWriter(exportFilePath, true); // append = true
        BufferedWriter writer = new BufferedWriter(exportWriter);
        for (Product product : products) {
            ArrayList<Deal> deals = product.getDeals();
            for (Deal deal : deals) {
                writer.write(
                        deal.getSellerName() + " : " +
                                deal.getDateFrom() + " : " +
                                deal.getRating() + " : " +
                                deal.getPhoneNumber() + "\n"
                );
            }
        }
        writer.close();
        products.clear();
    }


    public static void parseProducts(List<WebElement> goods) {
        CountDownLatch latch = new CountDownLatch(goods.size());
        ExecutorService executor = Executors.newFixedThreadPool(goods.size());

        for (int i = 0; i < goods.size(); i++) {
            executor.execute(new ProductParseThread(goods.get(i).getText(), goods.get(i).getAttribute("href"), latch));
        }
        executor.shutdown();
        try {
            latch.await();
        } catch(InterruptedException ex) {
            System.out.println(ex);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        String url = "jdbc:postgresql://localhost:5432/mydb";
        String user = "postgres";
        String password = "postgres";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Успешное подключение к базе данных!");

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT NOW()");
            if (rs.next()) {
                System.out.println("Время в базе: " + rs.getString(1));
            }
        } catch (SQLException e) {
            System.out.println("Ошибка подключения: " + e.getMessage());
        }

        System.exit(0);

        System.setProperty("webdriver.chrome.driver", "C:\\Users\\adil\\Downloads\\chromedriver-win64-124\\chromedriver-win64\\chromedriver.exe");
        WebDriver driver = new ChromeDriver();
        driver.get(categoryPath);
        int page = 1;

        while (page < 3 && !nextFlag) {
            List<WebElement> products = driver.findElements(By.className("item-card__name-link"));
            List<WebElement> temp = new ArrayList<>();
            for (int i = 0; i < products.size(); i++) {
                temp.add(products.get(i));
                if ((i + 1) % 4 == 0 && i != 0) {
                    parseProducts(temp);
                    temp.clear();
                }
            }
            if (temp.size() > 0) {
                parseProducts(temp);
            }

            writeProducts();

            page++;
            driver.get(categoryPath + "?page=" + page);
            try {
                boolean flag = driver.findElement(By.className("_disabled")).getText().equals("Следующая →");
                setNextFlag(flag);
            } catch(NoSuchElementException e) {
            }

            Random random = new Random();
            int pauseTime = random.nextInt(1001) + 500;
            Thread.sleep(pauseTime);
        }
        driver.quit();
    }
}
