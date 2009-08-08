<?php
  require_once "../common.php";

  $header = getallheaders();
  $username = $header["Login-UserName"];
  $password = $header["Login-Password"];
  $type = $_GET["type"];

  $mysql_link = new MysqlLink;
  $result = login($username, $password);
  if ($result == "error") {
    header("Login-Result: error");
  } else if ($result == "noretry") {
    header("Login-Result: noretry");
  } else if ($type == "w" || $type == "m" || $type == "q") {
    $sql = sprintf("SELECT rank, score FROM {$mysql_tablepre}rank{$type} WHERE uid = %d", $result->uid);
    $result2 = $mysql_link->query($sql);
    $line = mysql_fetch_assoc($result2);
    $rank = $line ? $line["rank"] : 0;
    $score = $line ? $line["score"] : 0;

    $rankYesterday = 0;
    if ($rank > 0) {
      $sql = sprintf("SELECT rank FROM {$mysql_tablepre}rank{$type}0 WHERE uid = %d", $result->uid);
      $result = $mysql_link->query($sql);
      $line = mysql_fetch_assoc($result);
      $rankYesterday = $line ? $line["rank"] : 0;
    }
    header("Login-Result: ok " . $score . "|" . $rank . "|" . $rankYesterday);
  } else {
    header("Login-Result: error");
  }
  $mysql_link->close();
?>