package com.albaraka.backend;

import com.albaraka.backend.model.Carte;
import com.albaraka.backend.model.Client;
import com.albaraka.backend.model.Compte;
import com.albaraka.backend.model.Role;
import com.albaraka.backend.model.Transaction;
import com.albaraka.backend.model.User;
import com.albaraka.backend.repository.CarteRepository;
import com.albaraka.backend.repository.ClientRepository;
import com.albaraka.backend.repository.CompteRepository;
import com.albaraka.backend.repository.TransactionRepository;
import com.albaraka.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@SpringBootApplication
public class BackendApplication {

	private static final Logger log = LoggerFactory.getLogger(BackendApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	/**
	 * The two original demo accounts (a super admin and a customer with sample
	 * comptes, cartes and transactions), kept because the manual test plan and the
	 * end-to-end smoke sign in with them.
	 *
	 * <p>Gated by {@code app.dev-seed.enabled} exactly like {@link
	 * com.albaraka.backend.dev.DevDataSeeder}: a known password must never reach a
	 * database that anyone can reach. Ordered before the seeder so the customer it
	 * enriches already exists.
	 *
	 * <p>Idempotent: each block is guarded by an existence check, so restarts never
	 * duplicate rows nor reset a password changed during testing.
	 */
	@Bean
	@Order(10)
	@ConditionalOnProperty(prefix = "app.dev-seed", name = "enabled", havingValue = "true")
	CommandLineRunner initDatabase(UserRepository userRepository, ClientRepository clientRepository,
								   CompteRepository compteRepository, CarteRepository carteRepository,
								   TransactionRepository transactionRepository, PasswordEncoder passwordEncoder) {
		return args -> {
			if (userRepository.findByEmail("admin@albaraka.tn").isEmpty()) {
				User admin = new User();
				admin.setEmail("admin@albaraka.tn");
				admin.setMotDePasse(passwordEncoder.encode("postgres"));
				admin.setNom("Admin");
				admin.setPrenom("Super");
				admin.setRole(Role.SUPER_ADMIN.name());
				userRepository.save(admin);
				log.info("dev fixture: super admin admin@albaraka.tn created (password in HOST_TESTING.md)");
			}
			if (clientRepository.findByEmail("client@albaraka.tn").isEmpty()) {
				Client client = new Client();
				client.setEmail("client@albaraka.tn");
				client.setMotDePasse(passwordEncoder.encode("client123"));
				client.setNom("Ben Salah");
				client.setPrenom("Yassine");
				client.setTelephone("+216 98 234 567");
				client.setAdresse("12 Avenue Habib Bourguiba");
				client.setVille("Tunis");
				client.setDateNaissance(LocalDate.of(1991, 4, 17));
				client.setLanguePreferee("fr");
				client.setSegment("PARTICULIER");
				client.setRole(Role.UTILISATEUR);
				Client saved = clientRepository.save(client);
				seedDemoBankingData(saved, compteRepository, carteRepository, transactionRepository);
				log.info("dev fixture: demo customer client@albaraka.tn created (password in HOST_TESTING.md)");
			}
		};
	}

	/** Portal demo products for the seeded client (read-only data, V8). */
	private void seedDemoBankingData(Client client, CompteRepository comptes, CarteRepository cartes,
									 TransactionRepository transactions) {
		Compte courant = new Compte();
		courant.setClient(client);
		courant.setLibelle("Compte principal");
		courant.setType("COMPTE_COURANT");
		courant.setIban("TN5904000012345678910268");
		courant.setDevise("TND");
		courant.setSolde(new BigDecimal("12450.480"));
		courant.setDateOuverture(LocalDateTime.now().minusDays(430));
		courant = comptes.save(courant);

		Compte epargne = new Compte();
		epargne.setClient(client);
		epargne.setLibelle("Épargne CEAI");
		epargne.setType("COMPTE_EPARGNE");
		epargne.setIban("TN5904000098765432109871");
		epargne.setDevise("TND");
		epargne.setSolde(new BigDecimal("6292.000"));
		epargne.setDateOuverture(LocalDateTime.now().minusDays(300));
		epargne = comptes.save(epargne);

		Carte evasion = new Carte();
		evasion.setClient(client);
		evasion.setCompteId(courant.getId());
		evasion.setType("EVASION");
		evasion.setReseau("MASTERCARD");
		evasion.setPorteur("4571 **** **** 6842");
		evasion.setExpiration("09/29");
		evasion.setPlafondJournalier(new BigDecimal("3000.000"));
		cartes.save(evasion);

		Carte gold = new Carte();
		gold.setClient(client);
		gold.setCompteId(courant.getId());
		gold.setType("GOLD_NATIONALE");
		gold.setReseau("MASTERCARD");
		gold.setPorteur("5213 **** **** 1149");
		gold.setExpiration("03/28");
		gold.setPlafondJournalier(new BigDecimal("1500.000"));
		cartes.save(gold);

		LocalDateTime now = LocalDateTime.now();
		saveTransaction(transactions, courant, now.minusHours(6), "Salaire — Ste Médina Capital", "VIREMENT", "4200.000");
		saveTransaction(transactions, courant, now.minusDays(1).minusHours(5), "Carrefour Lac 2", "COURSES", "-184.320");
		saveTransaction(transactions, courant, now.minusDays(1).minusHours(11), "Resto Dar El Jeld", "RESTAURATION", "-96.500");
		saveTransaction(transactions, epargne, now.minusDays(2).minusHours(2), "Intérêts CEAI", "INTERETS", "22.615");
		saveTransaction(transactions, courant, now.minusDays(3), "Prélèvement SONEDE", "FACTURES", "-48.400");
		saveTransaction(transactions, courant, now.minusDays(4), "Retrait DAB Menzah 6", "RETRAIT", "-200.000");
		saveTransaction(transactions, courant, now.minusDays(6), "Amazon EU", "ACHATS", "-129.990");
		saveTransaction(transactions, epargne, now.minusDays(8), "Virement épargne", "VIREMENT", "500.000");
		saveTransaction(transactions, courant, now.minusDays(9), "Prélèvement STEG", "FACTURES", "-87.230");
		saveTransaction(transactions, courant, now.minusDays(11), "Monoprix La Marsa", "COURSES", "-233.115");
	}

	private void saveTransaction(TransactionRepository repository, Compte compte, LocalDateTime date,
								 String libelle, String categorie, String montant) {
		Transaction t = new Transaction();
		t.setCompte(compte);
		t.setDateOperation(date);
		t.setLibelle(libelle);
		t.setCategorie(categorie);
		t.setMontant(new BigDecimal(montant));
		t.setDevise("TND");
		repository.save(t);
	}
}
