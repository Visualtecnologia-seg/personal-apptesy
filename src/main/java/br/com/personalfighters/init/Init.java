package br.com.personalfighters.init;

import br.com.personalfighters.entity.*;
import br.com.personalfighters.model.pagarme.request.AddressBilling;
import br.com.personalfighters.model.pagarme.request.CardUnsafeRequest;
import br.com.personalfighters.repository.FavoriteRepository;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.*;
import br.com.personalfighters.service.impl.exceptions.PaymentException;
import br.com.personalfighters.utils.DataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Slf4j
@RequiredArgsConstructor
@Profile({"dev", "prod"})
public class Init implements ApplicationListener<ContextRefreshedEvent> {

  /* User */
  private final UserService userService;
  private final UserRepository userRepository;
  private final CreditCardService creditCardService;
  private final FavoriteService favoriteService;
  private final FavoriteRepository favoriteRepository;
  private final PhotoService photoService;
  private final AddressService addressService;
  private final ReviewService reviewService;
  /* System */
  private final InfoService infoService;
  private final CategoryService categoryService;
  private final ProductService productService;
  private final OrderService orderService;
  private final OrderRepository orderRepository;
  private final ProductAgendaService productAgendaService;
  @Value("${create.fake.data}")
  private Boolean createFakeData;
  @Value("${create.fake.data.limit}")
  private int createFakeDataLimit;

  @Override
  public void onApplicationEvent(ContextRefreshedEvent arg0) {

    if (Boolean.TRUE.equals(createFakeData)) {
      try {
        createCategoryAndProducts();
        List<User> users = createUsers();
        createFavoritesAndReviews(users);
        createInfo();
      } finally {
        productAgendaService.updateAllProductAgendas();
      }
    }
  }

  public List<User> createUsers() {
    List<Product> products = productService.findAll();
    List<User> users = new ArrayList<>();

    for (int i = 0; i < createFakeDataLimit; i++) {
      User user = new User();
      String name = DataUtils.getName(i);
      String surname = DataUtils.getSurname();

      user.setName(name);
      user.setSurname(surname);
      user.setGender(DataUtils.getGender(name));
      user.setBirthday(DataUtils.getBirthday());
      user.setPhoneNumber(DataUtils.getPhoneNumber().replaceAll("[+]55", ""));
      user.setCpf(DataUtils.getCpf());
      user.setAvatarUrl(DataUtils.getPhoto());
      user.setEmail(DataUtils.getEmail(name, i));
      user.setUsername(user.getEmail());
      user.setPassword("123");
      userService.save(user);

      createCreditCard(user);
      createAddress(user);
      createPhoto(user);

      if (i >= 1) {
        insertProfessionalProduct(user, products);
      }
      users.add(user);
    }
    return users;
  }

  private void insertProfessionalProduct(User user, List<Product> products) {
    int index = DataUtils.random(products.size());
    Product p1 = products.get(index);
    Product p2 = products.get(index == 0 ? index + 1 : index - 1);
    productService.saveUserOnProduct(p1, user);
    productService.saveUserOnProduct(p2, user);
  }

  private void createAddress(User user) {
    for (int i = 0; i < 3; i++) {
      Address a = new Address();
      a.setStreet(DataUtils.getStreet());
      a.setNumber(DataUtils.getBuildingNumber());
      a.setComplement(DataUtils.getSecondaryAddress());
      a.setNeighborhood(DataUtils.getNeighborhood());
      a.setCity("Rio de Janeiro");
      a.setState("Rio de Janeiro");
      a.setZipcode(DataUtils.getZipCode());
      a.setActive(true);
      a.setLatitude("-22.9392731");
      a.setLongitude("-43.1790675");
      addressService.save(user.getId(), a);
    }
  }

  public void createCreditCard(User user) {
    for (int i = 0; i < 2; i++) {
      Random random = new Random();
      int n = random.nextInt(20);
      String cardNumber = DataUtils.getCardNumber().get(n);
      String cardExp = DataUtils.getCardExp().get(n);
      String cardCVV = DataUtils.getCardCvv().get(n);

      var holderName = StringUtils.stripAccents(user.getName() + " " + user.getSurname());

      CardUnsafeRequest card =
          CardUnsafeRequest.builder()
              .card_holder_name(holderName)
              .card_number(cardNumber)
              .card_expiration_date(cardExp)
              .card_cvv(cardCVV)
              .customer_id(String.valueOf(user.getId()))
              .billing(
                  AddressBilling.builder()
                      .street(DataUtils.getStreet())
                      .street_number(DataUtils.getBuildingNumber())
                      .neighborhood(DataUtils.getNeighborhood())
                      .city("Rio de Janeiro")
                      .state("RJ")
                      .country("BR")
                      .zipcode(DataUtils.getZipCode())
                      .build())
              .build();

      try{
        creditCardService.save(user.getId(), card);
      } catch (PaymentException e){
        log.error(e.getLocalizedMessage());
      }
    }
  }

  public void createPhoto(User user) {
    for (int i = 0; i < 8; i++) {
      Photo photo = new Photo();
      photo.setUser(user);
      photo.setImageUrl(DataUtils.getPhoto());
      photoService.savePhoto(photo);
    }
  }

  public void createInfo() {
    for (int i = 0; i < 20; i++) {
      Info info = new Info();
      info.setTitle("Dicas do Bruce " + i);
      info.setContent("<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin ut imperdiet neque. Cras eu porttitor metus. In eget mi sed ante bibendum maximus. Praesent imperdiet risus at purus vehicula efficitur.</p><figure class=\"image\"><img src=\"https://venum.com.br/blog/wp-content/uploads/2017/04/dicas-de-esquiva-ali.jpg\"></figure>");
      info.setActive(true);
      infoService.save(info);
    }
  }

  private void createCategoryAndProducts() {
    Category category = new Category();
    category.setName("Artes Marciais");
    category.setActive(true);
    categoryService.save(category);

    Product product1 = new Product();
    product1.setName("MUAY-THAI");
    product1.setUrlImage("https://img.stpu.com.br/?img=https://s3.amazonaws.com/pu-mgr/default/a0R0f00000wtH1zEAE/59d26ebce4b0a48c96eafad5.jpg");
    product1.setAbout("Muay thai ou boxe tailandês, é uma arte marcial originária da Tailândia, onde é considerado desporto nacional");
    product1.setCategory(category);
    product1.setEquipment("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quisque dapibus efficitur blandit. Integer hendrerit egestas felis, et tristique nisl euismod a. Proin fringilla lobortis tellus, vel eleifend quam facilisis sed. Pellentesque id efficitur urna.");
    product1.setActive(true);
    productService.save(product1);

    Product product2 = new Product();
    product2.setName("BOXE");
    product2.setUrlImage("https://image.freepik.com/free-photo/boxer-with-black-gloves-training-close-up_23-2148416712.jpg");
    product2.setAbout("O boxe ou pugilismo é um esporte de combate, no qual os lutadores usam apenas os punhos, tanto para a defesa, quanto para o ataque.");
    product2.setCategory(category);
    product2.setEquipment("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quisque dapibus efficitur blandit. Integer hendrerit egestas felis, et tristique nisl euismod a. Proin fringilla lobortis tellus, vel eleifend quam facilisis sed. Pellentesque id efficitur urna.");
    product2.setActive(true);
    productService.save(product2);

    Product product3 = new Product();
    product3.setName("JIU-JITSU");
    product3.setUrlImage("https://cdn.shopify.com/s/files/1/2776/7470/articles/jiujitsu_1024x1024.jpg");
    product3.setAbout("Jujutsu, mais conhecido na sua forma ocidentalizada Jiu-jitsu, ju-jitsu, é uma arte marcial japonesa, e também um esporte de combate, que utiliza técnicas de golpes de alavancas, torções e pressões para derrubar e dominar um oponente .");
    product3.setEquipment("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quisque dapibus efficitur blandit. Integer hendrerit egestas felis, et tristique nisl euismod a. Proin fringilla lobortis tellus, vel eleifend quam facilisis sed. Pellentesque id efficitur urna.");
    product3.setCategory(category);
    product3.setActive(true);
    productService.save(product3);

    Product product4 = new Product();
    product4.setName("KARATE");
    product4.setUrlImage("https://media.istockphoto.com/photos/martial-arts-fighter-picture-id1139651255?k=6&m=1139651255&s=612x612&w=0&h=G-YTxaYO5TU-eqJGxHJ4a1sDEBvSRwzQAytf9ePODLQ=");
    product4.setAbout("Caraté ou karaté ou Caratê ou caratê-dô é uma arte marcial japonesa desenvolvida a partir da arte marcial indígena de Okinawa sob influência da arte da guerra chinesa, das lutas tradicionais japonesas e das disciplinas guerreiras japonesas.");
    product4.setEquipment("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quisque dapibus efficitur blandit. Integer hendrerit egestas felis, et tristique nisl euismod a. Proin fringilla lobortis tellus, vel eleifend quam facilisis sed. Pellentesque id efficitur urna.");
    product4.setCategory(category);
    product4.setActive(true);
    productService.save(product4);

    Product product5 = new Product();
    product5.setName("MMA");
    product5.setUrlImage("https://i.ytimg.com/vi/cz__-sP9p4g/hqdefault.jpg");
    product5.setAbout("As artes marciais mistas, ou do inglês MMA, são uma modalidade de esporte de combate que incluem tanto golpes de combate em pé quanto técnicas de luta no chão.");
    product5.setCategory(category);
    product5.setEquipment("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Quisque dapibus efficitur blandit. Integer hendrerit egestas felis, et tristique nisl euismod a. Proin fringilla lobortis tellus, vel eleifend quam facilisis sed. Pellentesque id efficitur urna.");
    product5.setActive(true);
    productService.save(product5);
  }

  private void createFavoritesAndReviews(List<User> users) {
    //TODO Data Fake limit == 10 gerando erro
    users.forEach(user -> {
      int start = Math.toIntExact(user.getId());
      int index = start < users.size() - 10 ? 10 : start;

      for (int i = index - 8; i < index; i++) {
        if (i == user.getId()) {
          i++;
        }
        Review review = new Review();
        Random rating = new Random();
        int rand = rating.nextInt(5) + 1;

        review.setRating(rand);
        review.setDate(LocalDate.now().minusDays(rand));
        review.setCustomer(user);
        review.setComment("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");
        review.setProfessional(users.get(i));
        review.setOrder(null);
        reviewService.save(review);

        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favoriteService.save(favorite, user.getProfessional().getReference());
      }

    });

  }

//  Minhas práticas pedagógicas e metodológicas são utilizadas para o desenvolvimento do judô, mostrando sempre os diversos benefícios do esporte de contato, na qual se enquadra o judô. Obtendo os critérios necessários para aulas interessantes, seguras e com boa qualidade. Conhecimento acima da média em metodologias e práticas pedagógicas. Possuo participação no campeonato brasileiro de wrestling representando o Rio Grande do Sul no ano de 2016.

//  <p><strong>1° Colocado -</strong> Brasileiro - U21 - 2018<br><strong>2° Colocado -</strong> Brasileiro &nbsp;- Sênior - 2018<br><strong>2° Colocado -</strong> Brasileiro &nbsp;- U18 - 2017<br><strong>Participação - </strong>Brasileiro Wrestling - Cadete - 2016</p>

}

