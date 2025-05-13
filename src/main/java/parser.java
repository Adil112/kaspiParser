import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
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

import org.openqa.selenium.chrome.ChromeOptions;

class ProductParseThread extends Thread {
    public final static ChromeOptions chromeOptions = new ChromeOptions()
            .addArguments("--headless")
            .addArguments("--log-level=3");
    private CountDownLatch latch;
    private String href;
    private Set<String> shopNames;
    private Set<String> productNames;

    ProductParseThread(String productHref, CountDownLatch latch, Set<String> shopNames, Set<String> productNames) {
        this.href = productHref;
        this.latch = latch;
        this.shopNames = shopNames;
        this.productNames = productNames;
    }

    @Override
    public void run() {
        Product product = new Product();
        String link = this.href;
        WebDriver cardDriver = null;
        //WebDriver shopDriver = null;
        Set<String> sellerLinks = null;
        try {
            cardDriver = new ChromeDriver();
            cardDriver.get(link);

            WebDriverWait wait = new WebDriverWait(cardDriver, Duration.ofSeconds(5));
            try {
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//td/a")));
            } catch (TimeoutException e) {
                System.out.println("Не дождались загрузки продавцов");
            }

            sellerLinks = new HashSet<>();

            while (true) {
                WebElement titleElement = cardDriver.findElement(By.xpath("//h1[@class='item__heading']"));
                String productName = titleElement.getText();
                product.setName(productName);
                if (productNames.contains(productName)) {
                    break;
                }

                List<WebElement> shops = cardDriver.findElements(By.xpath("//td/a"));
                for (WebElement shop : shops) {
                    String shopHref = shop.getAttribute("href");
                    String shopName = shop.getText();
                    if (shopHref != null && !shopHref.isEmpty() && !sellerLinks.contains(shopHref) && !shopNames.contains(shopName)) {
                        sellerLinks.add(shopHref);
                    }
                }

//            break; // убрать и вернуть ниже, только первые 5 продвацов берет для тестирования

                try {
                    WebElement almatyOption = cardDriver.findElement(By.xpath("//a[@data-city-id='750000000']"));
                    almatyOption.click();
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }


                try {
                    WebElement nextBtn = cardDriver.findElement(By.xpath("//li[contains(text(), 'Следующая')]"));
                    if (nextBtn.getAttribute("class").contains("disabled")) {
                        break;
                    }
                    nextBtn.click();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    break;
                }
            }

            //shopDriver = new ChromeDriver();
            for (String shopHref : sellerLinks) {
                cardDriver.get(shopHref);
                String sellerName = "", phoneNumber = "", dateFrom = "", rating = "";

                try {
                    sellerName = cardDriver.findElement(By.className("merchant-profile__title")).getText();
                } catch (NoSuchElementException ignored) {
                }

                try {
                    phoneNumber = cardDriver.findElement(By.className("merchant-profile__contact-text")).getText();
                } catch (NoSuchElementException ignored) {
                }

                try {
                    dateFrom = cardDriver.findElement(By.className("merchant-profile__register-date")).getText();
                } catch (NoSuchElementException ignored) {
                }

                try {
                    WebElement ratingElement = cardDriver.findElement(By.cssSelector(".merchant-profile__rating.rating._seller"));
                    Matcher matcher = Pattern.compile("_(\\d+)$").matcher(ratingElement.getAttribute("class"));
                    if (matcher.find()) {
                        double ratingDouble = Double.parseDouble(matcher.group(1)) / 10.0;
                        rating = String.valueOf(ratingDouble);
                    }
                } catch (NoSuchElementException ignored) {
                }

                Deal deal = new Deal(sellerName.replaceAll(" в городе", ""), phoneNumber, dateFrom.replaceAll("В Kaspi Магазине с ", ""), rating);
                product.addDeal(deal);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            cardDriver.quit();
            //shopDriver.quit();
            parser.addProduct(product);
            sellerLinks.clear();
            this.latch.countDown();
        }
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
    private String name;
    private ArrayList<Deal> deals = new ArrayList<>();

    public Product() {}

    public void setName (String name) {
        this.name = name;
    }

    public String getName()  {
        return this.name;
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

    public static ArrayList<Product> products = new ArrayList<>();

    public static void addProduct(Product product) {
        products.add(product);
    }

    public static void setNextFlag(boolean flag) {
        nextFlag = flag;
    }

    public static void writeProducts() throws IOException {
        String url = "jdbc:postgresql://localhost:5432/mydb";
        String user = "postgres";
        String password = "postgres";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            String insertProductSql = "INSERT INTO products (name) VALUES (?) ON CONFLICT (name) DO NOTHING";
            String insertShopSql = "INSERT INTO shops (name, phone, rating, dateFrom) VALUES (?, ?, ?, ?) ON CONFLICT (name) DO NOTHING";

            PreparedStatement productStmt = conn.prepareStatement(insertProductSql);
            PreparedStatement shopStmt = conn.prepareStatement(insertShopSql);

            for (Product product : products) {
                if(!product.getName().isEmpty()){
                    productStmt.setString(1, product.getName());
                    productStmt.executeUpdate();
                }
                for (Deal deal : product.getDeals()) {
                    shopStmt.setString(1, deal.getSellerName());
                    shopStmt.setString(2, deal.getPhoneNumber());
                    shopStmt.setString(3, deal.getRating());
                    shopStmt.setString(4, deal.getDateFrom());
                    shopStmt.executeUpdate();
                }
            }

            productStmt.close();
            shopStmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        products.clear();
        System.gc();
    }


    public static void parseProducts(List<WebElement> goods, Set<String> productNames, Set<String> shopNames) {
        CountDownLatch latch = new CountDownLatch(goods.size());
        ExecutorService executor = Executors.newFixedThreadPool(3);

        for (int i = 0; i < goods.size(); i++) {
            String name = goods.get(i).getText();
            if (productNames.contains(name)) {
                latch.countDown();
                continue;
            }
            executor.execute(new ProductParseThread(goods.get(i).getAttribute("href"), latch, shopNames, productNames));
        }
        executor.shutdown();
        try {
            latch.await();
        } catch(InterruptedException ex) {
            System.out.println(ex);
        }
    }

    public static Set<String> loadExistingData(Connection conn, String query) throws SQLException {
        Set<String> names = new HashSet<>();
        String sql = query;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        return names;
    }
    public static void main(String[] args) throws IOException, InterruptedException {
        String url = "jdbc:postgresql://localhost:5432/mydb";
        String user = "postgres";
        String password = "postgres";
        Set<String> productNames = null;
        Set<String> shopNames = null;
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            productNames = loadExistingData(conn, "SELECT name FROM products");
            shopNames = loadExistingData(conn, "SELECT name FROM shops");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.setProperty("webdriver.chrome.driver", "C:\\Users\\adil\\Downloads\\chromedriver-win64-124\\chromedriver-win64\\chromedriver.exe");
        WebDriver driver = new ChromeDriver();
        driver.get(categoryPath);

        WebElement almatyOption1 = driver.findElement(By.xpath("//a[@data-city-id='750000000']"));
        almatyOption1.click();

        int page = 47;
        for(int i = 0; i < 47; i++){
            try {
                WebElement nextButton = driver.findElement(By.xpath("//li[contains(@class, 'pagination__el') and contains(text(), 'Следующая')]"));
                nextButton.click();
                Thread.sleep(new Random().nextInt(1001) + 500);
            } catch (NoSuchElementException e) {
                System.out.println("Кнопка 'Следующая' не найдена. Останавливаемся.");
                break;
            }
        }


        while (page < 51 && !nextFlag) {
            try {
                WebElement almatyOption = driver.findElement(By.xpath("//a[@data-city-id='750000000']"));
                almatyOption.click();
                Thread.sleep(1000);
            } catch (Exception ignored) {}

            List<WebElement> products = driver.findElements(By.className("item-card__name-link"));
            parseProducts(products, productNames, shopNames);
            writeProducts();
            page++;

            try {
                WebElement nextButton = driver.findElement(By.xpath("//li[contains(@class, 'pagination__el') and contains(text(), 'Следующая')]"));
                nextButton.click();
                Thread.sleep(new Random().nextInt(1001) + 500);
            } catch (NoSuchElementException e) {
                System.out.println("Кнопка 'Следующая' не найдена. Останавливаемся.");
                break;
            }
            System.gc();
        }
        driver.quit();
    }
}
