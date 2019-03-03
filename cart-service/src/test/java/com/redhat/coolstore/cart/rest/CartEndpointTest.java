package com.redhat.coolstore.cart.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashSet;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.jose.jws.Algorithm;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.redhat.coolstore.cart.service.CatalogService;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CartEndpointTest {

    @LocalServerPort
    private int port;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @Autowired
    private CatalogService catalogService;

    @Before
    public void beforeTest() throws Exception {
        RestAssured.baseURI = String.format("http://localhost:%d/cart", port);
        ReflectionTestUtils.setField(catalogService, null, "catalogServiceUrl", "http://localhost:" + wireMockRule.port(), null);
        initWireMockServer();
    }

/*    @Test
    public void retrieveCartById() throws Exception {
        given().get("/{cartId}", "123456")
            .then()
            .assertThat()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo("123456"))
            .body("cartItemTotal", equalTo(0.0f))
            .body("shoppingCartItemList", hasSize(0));
    }*/
    
    @Test
    public void retrieveCartById() throws Exception {
        RestAssured.given()
            .auth().oauth2(getValidAccessToken("coolstore"))
            .get("/{cartId}", "123456")
            .then()
            .assertThat()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo("123456"));
    }

    @Test
    @DirtiesContext
    public void addItemToCart() throws Exception {

    	RestAssured.given()
        .auth().oauth2(getValidAccessToken("coolstore")).post("/{cartId}/{itemId}/{quantity}", "234567", "111111", new Integer(1))
            .then()
            .assertThat()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo("234567"))
            .body("cartItemTotal", equalTo(new Float(100.0)))
            .body("shoppingCartItemList", hasSize(1))
            .body("shoppingCartItemList.product.itemId", hasItems("111111"))
            .body("shoppingCartItemList.price", hasItems(new Float(100.0)))
            .body("shoppingCartItemList.quantity", hasItems(new Integer(1)));
    }

    @Test
    @DirtiesContext
    public void addExistingItemToCart() throws Exception {

    	RestAssured.given()
        .auth().oauth2(getValidAccessToken("coolstore")).post("/{cartId}/{itemId}/{quantity}", "345678", "111111", new Integer(1));
    	RestAssured.given()
        .auth().oauth2(getValidAccessToken("coolstore")).post("/{cartId}/{itemId}/{quantity}", "345678", "111111", new Integer(1))
            .then()
            .assertThat()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo("345678"))
            .body("cartItemTotal", equalTo(new Float(200.0)))
            .body("shoppingCartItemList", hasSize(1))
            .body("shoppingCartItemList.product.itemId", hasItems("111111"))
            .body("shoppingCartItemList.price", hasItems(new Float(100.0)))
            .body("shoppingCartItemList.quantity", hasItems(new Integer(2)));
    }

    @Test
    @DirtiesContext
    public void addItemToCartWhenCatalogServiceThrowsError() throws Exception {

    	RestAssured.given()
        .auth().oauth2(getValidAccessToken("coolstore")).post("/{cartId}/{itemId}/{quantity}", "234567", "error", new Integer(1))
            .then()
            .assertThat()
            .statusCode(503);
    }

    @Test
    @DirtiesContext
    public void removeAllInstancesOfItemFromCart() throws Exception {

    	RestAssured.given()
        .auth().oauth2(getValidAccessToken("coolstore")).post("/{cartId}/{itemId}/{quantity}", "456789", "111111", new Integer(2));
    	RestAssured.given()
        .auth().oauth2(getValidAccessToken("coolstore")).delete("/{cartId}/{itemId}/{quantity}", "456789", "111111", new Integer(2))
            .then()
            .assertThat()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo("456789"))
            .body("cartItemTotal", equalTo(new Float(0.0)))
            .body("shoppingCartItemList", hasSize(0));
    }

    @Test
    @DirtiesContext
    public void removeSomeInstancesOfItemFromCart() throws Exception {

    	RestAssured.given()
        .auth().oauth2(getValidAccessToken("coolstore")).post("/{cartId}/{itemId}/{quantity}", "567890", "111111", new Integer(3));
    	RestAssured.given()
        .auth().oauth2(getValidAccessToken("coolstore")).delete("/{cartId}/{itemId}/{quantity}", "567890", "111111", new Integer(1))
            .then()
            .assertThat()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo("567890"))
            .body("cartItemTotal", equalTo(new Float(200.0)))
            .body("shoppingCartItemList", hasSize(1))
            .body("shoppingCartItemList.quantity", hasItems(new Integer(2)));
    }

    @Test
    @DirtiesContext
    public void checkoutCart() throws Exception {

    	RestAssured.given()
        .auth().oauth2(getValidAccessToken("coolstore")).post("/{cartId}/{itemId}/{quantity}", "678901", "111111", new Integer(3));
    	RestAssured.given()
        .auth().oauth2(getValidAccessToken("coolstore")).post("/checkout/{cartId}", "678901")
            .then()
            .assertThat()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo("678901"))
            .body("cartItemTotal", equalTo(new Float(0.0)))
            .body("shoppingCartItemList", hasSize(0));
    }

    private void initWireMockServer() throws Exception {
        InputStream isresp = Thread.currentThread().getContextClassLoader().getResourceAsStream("catalog-response.json");

        stubFor(get(urlEqualTo("/product/111111")).willReturn(
                aResponse().withStatus(200).withHeader("Content-type", "application/json").withBody(IOUtils.toString(isresp, Charset.defaultCharset()))));

        stubFor(get(urlEqualTo("/product/error")).willReturn(
                aResponse().withStatus(500)));
    }
    
    private PrivateKey readPrivateKey() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyFactory factory = KeyFactory.getInstance("RSA", "BC");
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("private.pem");
        PemReader privateKeyReader = new PemReader(new InputStreamReader(is));
        try {
            PemObject privObject = privateKeyReader.readPemObject();
            PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(privObject.getContent());
            PrivateKey privateKey = factory.generatePrivate(privKeySpec);
            return privateKey;
        } finally {
            privateKeyReader.close();
        }
    }
    private String createAccessToken(String role, int issuedAt) throws Exception {
        AccessToken token = new AccessToken();
        token.type(TokenUtil.TOKEN_TYPE_BEARER);
        token.subject("testuser");
        token.issuedAt(issuedAt);
        token.issuer("https://secure-sso-user3-coolstore-infra.apps.3dd1.example.opentlc.com/auth/realms/coolstore-test");
        token.expiration(issuedAt + 300);
        token.setAllowedOrigins(new HashSet<>());

        AccessToken.Access access = new AccessToken.Access();
        token.setRealmAccess(access);
        access.addRole(role);

        Algorithm jwsAlgorithm = Algorithm.RS256;
        PrivateKey privateKey = readPrivateKey();
        String encodedToken = new JWSBuilder().type("JWT").jsonContent(token).sign(jwsAlgorithm, privateKey);
        return encodedToken;
    } 
    private String getValidAccessToken(String role) throws Exception {
        return createAccessToken(role, (int) (System.currentTimeMillis() / 1000));
    }

    private String getExpiredAccessToken(String role) throws Exception {
        return createAccessToken(role, (int) ((System.currentTimeMillis() / 1000)-600));
    }    

}
