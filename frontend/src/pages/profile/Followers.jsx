import { getFollowersRequest } from "../../features/users/services/users.service";
import FollowListPage from "./FollowListPage";
import { useParams } from "react-router-dom";

function Followers() {
  const { username } = useParams();

  return (
    <FollowListPage
      type="followers"
      title="Seguidores"
      fetchFunction={getFollowersRequest}
      emptyMessage={username ? `@${username} no tiene seguidores aún`: `No tienes seguidores aún`}
      searchPlaceholder="Buscar en seguidores..."
      resultsCountMessage={(count) => `${count} seguidores`}
      username={username}
    />
  );
}

export default Followers;