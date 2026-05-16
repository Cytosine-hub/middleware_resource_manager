package com.middleware.manager.service;

import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.repository.AdminAccountRepository;
import com.middleware.manager.security.Role;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminAccountService implements UserDetailsService, ApplicationRunner {

    private final AdminAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultPassword;

    public AdminAccountService(AdminAccountRepository repository,
                               PasswordEncoder passwordEncoder,
                               @Value("${app.security.admin.default-password:admin123}") String defaultPassword) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedDefaultAccounts();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminAccount account = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在"));

        Role role = Role.fromDisplayName(account.getRole());
        return new User(account.getUsername(), account.getPasswordHash(),
                AuthorityUtils.createAuthorityList(role.getAuthority()));
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        AdminAccount account = repository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        repository.save(account);
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        AdminAccount account = repository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!StringUtils.hasText(newPassword) || newPassword.trim().length() < 6) {
            throw new IllegalArgumentException("密码至少6位");
        }
        account.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        repository.save(account);
    }

    public String getDisplayNameByUsername(String username) {
        return repository.findByUsername(username)
                .map(AdminAccount::getDisplayName)
                .orElse(username);
    }

    public List<AdminAccount> listUsers() {
        return repository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public AdminAccount createUser(String username, String displayName, String password, String role) {
        if (repository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("账号已存在");
        }
        Role.fromDisplayName(role);
        AdminAccount account = new AdminAccount();
        account.setUsername(username);
        account.setDisplayName(displayName != null ? displayName : username);
        account.setPasswordHash(encodePassword(password));
        account.setRole(role);
        return repository.save(account);
    }

    private String encodePassword(String raw) {
        return passwordEncoder.encode(raw);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public AdminAccount updateUserRole(Long userId, String newRole) {
        Role.fromDisplayName(newRole);
        AdminAccount account = repository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        account.setRole(newRole);
        return repository.save(account);
    }

    @Transactional
    public void deleteUser(Long userId) {
        AdminAccount account = repository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if ("系统管理员".equals(account.getRole()) && repository.countByRole("系统管理员") <= 1) {
            throw new IllegalArgumentException("不能删除最后一个系统管理员");
        }
        repository.delete(account);
    }

    private void seedDefaultAccounts() {
        if (repository.count() > 0) return;

        String[][] defaultAccounts = {
            {"系统管理员", "sysadmin", "系统管理员"},
            {"中间件管理岗", "mwadmin", "中间件管理员"},
            {"数据库管理岗", "dbadmin", "数据库管理员"},
            {"主机管理岗", "hostadmin", "主机管理员"},
            {"网络管理岗", "netadmin", "网络管理员"},
            {"网络安全岗", "secadmin", "安全管理员"},
            {"开发经理", "devmgr", "开发经理"},
            {"运维经理", "opsmgr", "运维经理"},
        };

        for (String[] entry : defaultAccounts) {
            AdminAccount account = new AdminAccount();
            account.setRole(entry[0]);
            account.setUsername(entry[1]);
            account.setDisplayName(entry[2]);
            account.setPasswordHash(encodePassword(sha256Hex(defaultPassword)));
            repository.save(account);
        }
    }
}
