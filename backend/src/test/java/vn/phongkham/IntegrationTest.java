package vn.phongkham;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.postgresql.PostgreSQLContainer;
import vn.phongkham.auth.model.Role;
import vn.phongkham.auth.model.User;
import vn.phongkham.auth.model.UserStatus;
import vn.phongkham.auth.repository.UserRepository;
import vn.phongkham.auth.security.TokenService;
import vn.phongkham.staff.model.Employee;
import vn.phongkham.staff.repository.EmployeeRepository;

/**
 * Test gọi API qua HTTP trên PostgreSQL thật (Testcontainers, dùng chung cho mọi lớp test).
 * Trước mỗi test xóa sạch dữ liệu, chỉ giữ lịch sử Flyway và cấu hình phòng khám.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "app.sms.verify-number=0900000000",
        "app.sms.gateway-token=" + IntegrationTest.GATEWAY_TOKEN,
        "app.sms.signing-key=" + IntegrationTest.SIGNING_KEY,
        "app.rate-limit.enabled=false", // mọi request test cùng một IP; chống spam kiểm riêng ở RateLimitTest
        "app.seed-demo=false",
        "app.jobs.enabled=false",
        "app.storage.dir=${java.io.tmpdir}/phongkham-test-files"})
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    protected static final String GATEWAY_TOKEN = "test-gateway-token";
    protected static final String SIGNING_KEY = "test-signing-key";
    private static final AtomicInteger PHONE_SEQ = new AtomicInteger(100);

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected EmployeeRepository employees;
    @Autowired
    protected TokenService tokens;
    @Autowired
    protected PasswordEncoder encoder;

    @BeforeEach
    void cleanDatabase() {
        List<String> tables = jdbc.queryForList("""
                select tablename from pg_tables
                where schemaname = 'public' and tablename not in ('flyway_schema_history', 'clinic_settings')
                """, String.class);
        jdbc.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
    }

    /** Số điện thoại mới, không trùng giữa các test */
    protected static String newPhone() {
        return "09%08d".formatted(PHONE_SEQ.incrementAndGet());
    }

    /** Tạo tài khoản đang hoạt động (nhân viên thì kèm hồ sơ nhân viên) với mật khẩu cho trước */
    protected User createUser(String phone, String password, Role... roles) {
        User u = new User();
        u.setPhone(phone);
        u.setPasswordHash(encoder.encode(password));
        u.setStatus(UserStatus.ACTIVE);
        u.setPhoneVerifiedAt(Instant.now());
        u.setRoles(EnumSet.copyOf(List.of(roles)));
        users.save(u);
        if (Role.STAFF.stream().anyMatch(u.getRoles()::contains)) {
            Employee e = new Employee();
            e.setUserId(u.getId());
            e.setEmployeeCode("T" + u.getId());
            e.setFullName("Nhân viên " + u.getId());
            employees.save(e);
        }
        return u;
    }

    /** Token của một tài khoản mới có các vai trò cho trước */
    protected String tokenFor(Role... roles) {
        return tokens.issue(createUser(newPhone(), "matkhau123", roles)).token();
    }

    protected ResultActions get(String url, String token) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.get(url), token));
    }

    protected ResultActions post(String url, String token, String json) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.post(url), token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Đóng vai cổng SMS: chuyển tin nhắn "body" gửi từ số "from" tới phòng khám */
    protected ResultActions smsArrives(String from, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/sms/inbound").header("X-Gateway-Token", GATEWAY_TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"from":"%s","body":"%s"}""".formatted(from, body)));
    }

    protected ResultActions put(String url, String token, String json) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.put(url), token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions patch(String url, String token, String json) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.patch(url), token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions delete(String url, String token) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.delete(url), token));
    }

    protected static <T> T read(ResultActions result, String path) throws Exception {
        MvcResult r = result.andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(StandardCharsets.UTF_8), path);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return token == null ? b : b.header("Authorization", "Bearer " + token);
    }
}
