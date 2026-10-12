package com.whatap.userapi.api;

import com.whatap.userapi.api.dto.ParseFailuresResponse;
import com.whatap.userapi.api.dto.ReloadResponse;
import com.whatap.userapi.domain.User;
import com.whatap.userapi.service.PageResult;
import com.whatap.userapi.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private static final String DEFAULT_PAGE = "0";
	private static final String DEFAULT_SIZE = "10";
	private static final int MAX_SIZE = 100;

	private static final String PAGE_MESSAGE = "page는 0 이상이어야 합니다.";
	private static final String SIZE_MESSAGE = "size는 1 이상 " + MAX_SIZE + " 이하여야 합니다.";

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public PageResult<User> findAll(
			@RequestParam(defaultValue = DEFAULT_PAGE) @Min(value = 0, message = PAGE_MESSAGE) int page,
			@RequestParam(defaultValue = DEFAULT_SIZE)
			@Min(value = 1, message = SIZE_MESSAGE) @Max(value = MAX_SIZE, message = SIZE_MESSAGE) int size) {
		return userService.findAll(page, size);
	}

	@GetMapping("/{id}")
	public User findById(@PathVariable int id) {
		return userService.findById(id);
	}

	@GetMapping("/search")
	public PageResult<User> searchByName(
			@RequestParam @NotBlank(message = "name은 공백일 수 없습니다.") String name,
			@RequestParam(defaultValue = DEFAULT_PAGE) @Min(value = 0, message = PAGE_MESSAGE) int page,
			@RequestParam(defaultValue = DEFAULT_SIZE)
			@Min(value = 1, message = SIZE_MESSAGE) @Max(value = MAX_SIZE, message = SIZE_MESSAGE) int size) {
		return userService.searchByName(name, page, size);
	}

	@GetMapping("/parse-failures")
	public ParseFailuresResponse findParseFailures() {
		return ParseFailuresResponse.from(userService.currentSnapshot());
	}

	@PostMapping("/reload")
	public ReloadResponse reload() {
		return ReloadResponse.from(userService.reload());
	}
}
