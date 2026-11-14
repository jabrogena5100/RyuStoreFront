package link.ryustorefront.storefront;

import org.springframework.boot.SpringApplication;

public class TestStorefrontApplication {

	public static void main(String[] args) {
		SpringApplication.from(StorefrontApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
