import { getFollowingRequest } from "../../features/users/services/users.service";
import FollowListPage from "./FollowListPage";
import { useParams } from "react-router-dom";

function Following() {
  const { username } = useParams();

  return (
    <FollowListPage
      type="following"
      title="Siguiendo"
      fetchFunction={getFollowingRequest}
      username={username}
      emptyMessage={username ? `@${username} no sigue a nadie aún` : `No sigues a nadie aún`}
      searchPlaceholder="Buscar en siguiendo..."
      resultsCountMessage={(count) => `Siguiendo a ${count} usuarias`}
    />
  );
}

export default Following;