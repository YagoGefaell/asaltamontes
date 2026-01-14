import "./Home.css";

const Home = () => {
  return (
    <div className="home-container">
      <div className="stories">
        {[1, 2, 3, 4, 5].map((story) => (
          <div className="story" key={story}>
            <div className="story-avatar" />
            <span className="story-name">User {story}</span>
          </div>
        ))}
      </div>

      <div className="feed">
        {[1, 2, 3].map((post) => (
          <div className="post-card" key={post}>
            <div className="post-header">
              <div className="post-user">
                <div className="post-avatar" />
                <span className="post-username">asaltamontes</span>
              </div>
              <span className="post-menu">⋯</span>
            </div>

            <div className="post-image" />

            <div className="post-actions">
              ❤️ 💬 📤
            </div>

            <div className="post-content">
              <span className="post-likes">120 likes</span>
              <p>
                <strong>asaltamontes</strong> Comunidad femenina creciendo juntas 💖
              </p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default Home;
