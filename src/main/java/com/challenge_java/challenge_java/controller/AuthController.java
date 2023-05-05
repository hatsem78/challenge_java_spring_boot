package com.challenge_java.challenge_java.controller;

import com.challenge_java.challenge_java.model.entity.ERole;
import com.challenge_java.challenge_java.model.entity.Phone;
import com.challenge_java.challenge_java.model.entity.Role;
import com.challenge_java.challenge_java.model.entity.User;
import com.challenge_java.challenge_java.model.services.RoleServiceImpl;
import com.challenge_java.challenge_java.model.services.UserDetailsImpl;
import com.challenge_java.challenge_java.model.services.UserServicesImpl;
import com.challenge_java.challenge_java.request.LoginRequest;
import com.challenge_java.challenge_java.request.SignupRequest;
import com.challenge_java.challenge_java.response.MessageResponse;
import com.challenge_java.challenge_java.response.UserInfoResponse;
import com.challenge_java.challenge_java.response.UserSignUpResponse;
import com.challenge_java.challenge_java.security.jwt.JwtUtils;
import com.challenge_java.challenge_java.utils.Utils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserServicesImpl userServices;

    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;

    private final Utils utils;

    private final RoleServiceImpl roleService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserServicesImpl userServices,
            PasswordEncoder encoder,
            JwtUtils jwtUtils,
            Utils utils,
            RoleServiceImpl roleService
    ) {
        this.authenticationManager = authenticationManager;
        this.userServices = userServices;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
        this.utils = utils;
        this.roleService = roleService;
    }

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        ResponseCookie jwtCookie = jwtUtils.generateJwtCookie(userDetails);

        User userUpdate = userServices.findByUsername(loginRequest.getUsername()).get();

        userUpdate.setLastLogin(new Date());
        userUpdate = userServices.update(userUpdate);



        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(new UserInfoResponse(
                        userUpdate.getIds(),
                        userUpdate.getCreateAt(),
                        userUpdate.getLastLogin(),
                        userUpdate.getToken(),
                        userUpdate.getActive(),
                        userUpdate.getUsername(),
                        userUpdate.getEmail(),
                        userUpdate.getPassword(),
                        userUpdate.getPhone()
                    )
                );
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userServices.existsByUsername(signUpRequest.getUsername())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken!"));
        }

        if (userServices.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

        /* Create new user's account */
        User user = new User(signUpRequest.getUsername(),
                signUpRequest.getEmail(),
                encoder.encode(signUpRequest.getPassword()));

        /* set  phone list*/
        Stream<Phone> phoneStream = signUpRequest.getPhones().stream().map(
                phone -> new Phone(phone.getNumber(), phone.getCityCode(), phone.getContrycode())
        );

        Set<Phone> phoneList = phoneStream.collect(Collectors.toSet());
        phoneList.forEach(System.out::println);

        user.setPhone(phoneList);

        /* set role */
        Set<String> strRoles = signUpRequest.getRole();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null) {
            Role userRole = roleService.findByName(ERole.ROLE_USER)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(userRole);
        } else {
            strRoles.forEach(role -> {
                switch (role) {
                    case "admin":
                        Role adminRole = roleService.findByName(ERole.ROLE_ADMIN)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(adminRole);

                        break;
                    case "mod":
                        Role modRole = roleService.findByName(ERole.ROLE_MODERATOR)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(modRole);

                        break;
                    default:
                        Role userRole = roleService.findByName(ERole.ROLE_USER)
                                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
                        roles.add(userRole);
                }
            });
        }
        user.setIds(UUID.randomUUID().toString());
        user.setRoles(roles);

        userServices.save(user);

        return ResponseEntity.ok()
                .body(
                    new UserSignUpResponse(
                        user.getUsername(),
                        user.getIds(),
                        user.getEmail(),
                        user.getCreateAt(),
                        user.getLastLogin(),
                        user.getToken(),
                        user.getActive()
                    )
                );
    }

    @PostMapping("/signout")
    public ResponseEntity<?> logoutUser() {
        ResponseCookie cookie = jwtUtils.getCleanJwtCookie();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new MessageResponse("You've been signed out!"));
    }
}
