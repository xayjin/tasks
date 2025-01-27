package org.tasks.filters;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import com.todoroo.astrid.api.FilterListItem;

public class NavigationDrawerAction extends FilterListItem {

  /** Parcelable Creator Object */
  public static final Parcelable.Creator<NavigationDrawerAction> CREATOR =
      new Parcelable.Creator<>() {

        /** {@inheritDoc} */
        @Override
        public NavigationDrawerAction createFromParcel(Parcel source) {
          NavigationDrawerAction item = new NavigationDrawerAction();
          item.readFromParcel(source);
          return item;
        }

        /** {@inheritDoc} */
        @Override
        public NavigationDrawerAction[] newArray(int size) {
          return new NavigationDrawerAction[size];
        }
      };

  public Intent intent;
  public int requestCode;

  private NavigationDrawerAction() {}

  public NavigationDrawerAction(String listingTitle, int icon, int requestCode) {
    this(listingTitle, icon, null, requestCode);
  }

  public NavigationDrawerAction(String listingTitle, int icon, Intent intent, int requestCode) {
    this.listingTitle = listingTitle;
    this.icon = icon;
    this.intent = intent;
    this.requestCode = requestCode;
  }

  @Override
  public Type getItemType() {
    return Type.ACTION;
  }

  /** {@inheritDoc} */
  @Override
  public void writeToParcel(Parcel dest, int flags) {
    super.writeToParcel(dest, flags);
    dest.writeParcelable(intent, 0);
    dest.writeInt(requestCode);
  }

  @Override
  protected void readFromParcel(Parcel source) {
    super.readFromParcel(source);
    intent = source.readParcelable(Intent.class.getClassLoader());
    requestCode = source.readInt();
  }

  @Override
  public boolean areItemsTheSame(@NonNull FilterListItem other) {
    return other instanceof NavigationDrawerAction
        && requestCode == ((NavigationDrawerAction) other).requestCode;
  }
}
