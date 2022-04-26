package mflix.api.daos;

import com.mongodb.MongoClientSettings;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import mflix.api.models.Session;
import mflix.api.models.User;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Configuration
public class UserDao extends AbstractMFlixDao {

    private final MongoCollection<User> usersCollection;

    private final MongoCollection<Session> sessionsCollection;

    private final Logger log;

    @Autowired
    public UserDao(
            MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
        super(mongoClient, databaseName);
        CodecRegistry pojoCodecRegistry =
                fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        fromProviders(PojoCodecProvider.builder().automatic(true).build()));

        usersCollection = db.getCollection("users", User.class).withCodecRegistry(pojoCodecRegistry);
        log = LoggerFactory.getLogger(this.getClass());

        sessionsCollection = db.getCollection("sessions", Session.class).withCodecRegistry(pojoCodecRegistry);

    }

    /**
     * Inserts the `user` object in the `users` collection.
     *
     * @param user - User object to be added
     * @return True if successful, throw IncorrectDaoOperation otherwise
     */
    public boolean addUser(User user) {

        usersCollection.withWriteConcern(WriteConcern.UNACKNOWLEDGED);

        User userWithSuchEmail = usersCollection.find(eq("email", user.getEmail())).first();
        if (userWithSuchEmail!=null){
            throw new IncorrectDaoOperation("User like that already exists");
        }
        usersCollection.insertOne(user);
        return true;
    }

    /**
     * Creates session using userId and jwt token.
     *
     * @param userId - user string identifier
     * @param jwt    - jwt string token
     * @return true if successful
     */
    public boolean createUserSession(String userId, String jwt) {

        Session session = new Session();
        session.setUserId(userId);
        session.setJwt(jwt);

        Document queryFilter = new Document("user_id", userId);

        if (sessionsCollection.find(queryFilter).iterator().hasNext()) {

            deleteUserSessions(userId);
        }

        sessionsCollection.insertOne(session);

        return true;

    }

    /**
     * Returns the User object matching the an email string value.
     *
     * @param email - email string to be matched.
     * @return User object or null.
     */
    public User getUser(String email) {
        return usersCollection.find(eq("email", email)).first();
    }

    /**
     * Given the userId, returns a Session object.
     *
     * @param userId - user string identifier.
     * @return Session object or null.
     */
    public Session getUserSession(String userId) {

        return sessionsCollection.find(eq("user_id", userId)).first();
    }

    public boolean deleteUserSessions(String userId) {

        sessionsCollection.deleteMany(eq("user_id", userId));
        return true;
    }

    /**
     * Removes the user document that match the provided email.
     *
     * @param email - of the user to be deleted.
     * @return true if user successfully removed
     */
    public boolean deleteUser(String email) {
        // remove user sessions
        Document queryFilter = new Document();
        queryFilter.put("email", email);

        usersCollection.deleteOne(queryFilter);
        deleteUserSessions(email);


        return true;
    }

    /**
     * Updates the preferences of an user identified by `email` parameter.
     *
     * @param email           - user to be updated email
     * @param userPreferences - set of preferences that should be stored and replace the existing
     *                        ones. Cannot be set to null value
     * @return User object that just been updated.
     */
    public boolean updateUserPreferences(String email, Map<String, ?> userPreferences) {

        Bson queryFilter = new Document("email", email);
        Document userPref = new Document();

        try {

            userPreferences.forEach(userPref::append);

            usersCollection.updateMany(queryFilter, set("preferences", userPref));

        } catch (NullPointerException e) {
            throw new IncorrectDaoOperation(e.getMessage());
        }


        return true;
    }
}
