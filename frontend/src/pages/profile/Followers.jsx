import { getFollowersRequest } from "../../features/users/services/users.service";
import FollowListPage from "./FollowListPage";
import { useParams } from "react-router-dom";

function Followers() {
  const { username } = useParams();

  return (
    <FollowListPage
      type="followers"
      title="Seguidoras"
      fetchFunction={getFollowersRequest}
      emptyMessage={username ? `@${username} no tiene seguidoras aún`: `No tienes seguidoras aún`}
      searchPlaceholder="Buscar en seguidoras..."
      resultsCountMessage={(count) => `${count} seguidoras`}
      username={username}
    />
  );
}

export default Followers;